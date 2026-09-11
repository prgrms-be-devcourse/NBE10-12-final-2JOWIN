package com.twojo.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code @Transactional(propagation = MANDATORY)} 메서드를 부르는 쪽은 <b>쓰기</b> 트랜잭션 안이어야 한다
 * (13 §3.1, #302).
 *
 * <p><b>왜 테스트로 잡는가</b> — {@code MANDATORY}는 트랜잭션의 <b>존재</b>만 검사한다. 읽기 전용
 * 호출자는 검사를 통과하고, Hibernate가 flush를 건너뛰어 <b>변경이 예외도 로그도 없이 버려진다</b>
 * (PR #225 리뷰). #227이 {@code DealCommandImpl} javadoc에 호출부 셋을 이름으로 적어 두었지만, 네 번째
 * 호출부가 생기는 순간 그 목록은 거짓이 된다. 사람이 아니라 테스트가 지킨다.
 *
 * <p><b>호출 사슬을 거슬러 올라간다</b> — 직접 호출자만 보면 private 헬퍼 경유가 전부 빠진다
 * ({@code MemberAdminService.deactivate} → {@code transferOpenDeals} → {@code reassignOpenDeals}).
 * 그래서 호출자에 트랜잭션 어노테이션이 없으면 <b>그 호출자를 부르는 쪽</b>으로 올라가고, 어느 경로든
 * 쓰기 트랜잭션에 닿아야 통과한다. 어노테이션이 없는 채로 아무도 부르지 않는 메서드에 닿으면
 * 트랜잭션 없는 진입점이므로 위반이다.
 *
 * <p><b>계약(인터페이스)을 거친 호출도 잡는다</b> — 호출부는 {@code DealCommand.markWon}을 부르지
 * {@code DealCommandImpl.markWon}을 부르지 않는다. 구현 메서드가 {@code MANDATORY}면 그 메서드가 구현하는
 * 인터페이스 메서드도 같은 대상으로 본다. 거슬러 올라갈 때도 마찬가지다.
 *
 * <p><b>전파 속성의 해석</b>:
 * <ul>
 *   <li>{@code REQUIRED}·{@code REQUIRES_NEW}·{@code NESTED} — 이 메서드가 트랜잭션을 연다(또는 합류한다).
 *       {@code readOnly}가 판정이다.</li>
 *   <li>{@code MANDATORY}·{@code SUPPORTS} — 호출자 트랜잭션을 그대로 쓴다. 호출자로 올라간다.</li>
 *   <li>{@code NOT_SUPPORTED}·{@code NEVER} — 트랜잭션 밖에서 돈다. 위반.</li>
 * </ul>
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MandatoryCallerWriteTransactionTest {

    private static JavaClasses productionClasses;

    /** {@code MANDATORY} 구현 메서드 + 그 메서드가 구현하는 인터페이스 메서드. */
    private static Set<JavaMethod> mandatoryTargets;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.twojo");

        mandatoryTargets = new LinkedHashSet<>();
        productionClasses.stream()
                .flatMap(clazz -> clazz.getMethods().stream())
                .filter(method -> propagationOf(method) == Propagation.MANDATORY)
                .forEach(method -> {
                    mandatoryTargets.add(method);
                    mandatoryTargets.addAll(interfaceMethodsOf(method));
                });
    }

    @Test
    void MANDATORY_메서드의_호출자는_쓰기_트랜잭션_안이다() {
        List<String> violations = new ArrayList<>();
        for (JavaMethod caller : callersOf(mandatoryTargets)) {
            List<String> path = new ArrayList<>();
            if (!reachesWriteTransaction(caller, new HashSet<>(), path)) {
                violations.add(caller.getFullName() + " — " + String.join(" ← ", path));
            }
        }

        assertThat(violations)
                .as("MANDATORY 메서드를 부르는데 쓰기 트랜잭션에 닿지 않는 경로가 있다 — "
                        + "읽기 전용이면 변경이 조용히 버려지고, 트랜잭션이 없으면 그 자리에서 터진다 (13 §3.1)")
                .isEmpty();
    }

    /**
     * 규칙이 실제로 무언가를 보고 있는가. {@code MANDATORY} 메서드나 그 호출부가 0건이면 위 테스트는
     * 공짜로 통과하고, 그때부터 아무것도 지키지 않으면서 초록불만 남는다.
     */
    @Test
    void 규칙이_검사하는_호출부가_실제로_있다() {
        assertThat(mandatoryTargets).as("MANDATORY 메서드").isNotEmpty();
        assertThat(callersOf(mandatoryTargets)).as("MANDATORY 메서드의 호출부").isNotEmpty();
    }

    /**
     * {@code MANDATORY}인데 아무도 부르지 않는 메서드는 없는가 — 호출부가 없으면 규칙이 그 메서드에
     * 대해 아무것도 검사하지 않는다. 죽은 계약이거나, 호출 해석이 놓친 경로다(둘 다 알아야 한다).
     */
    @Test
    void MANDATORY_메서드는_전부_호출부가_있다() {
        var uncalled = mandatoryTargets.stream()
                .filter(target -> !target.getOwner().isInterface())
                .filter(target -> callersOf(Set.of(target)).isEmpty()
                        && interfaceMethodsOf(target).stream()
                                .allMatch(iface -> callersOf(Set.of(iface)).isEmpty()))
                .map(JavaMethod::getFullName)
                .toList();

        assertThat(uncalled).as("MANDATORY인데 호출부가 없는 메서드").isEmpty();
    }

    // ── 호출 그래프 ──────────────────────────────────────────────────────

    /** {@code targets} 중 하나라도 부르는 메서드. 자기 자신(대상 집합 안)에서의 호출은 제외한다. */
    private static Set<JavaMethod> callersOf(Set<JavaMethod> targets) {
        Set<JavaMethod> callers = new LinkedHashSet<>();
        for (JavaMethod target : targets) {
            for (JavaMethodCall call : target.getCallsOfSelf()) {
                if (call.getOrigin() instanceof JavaMethod origin && !targets.contains(origin)) {
                    callers.add(origin);
                }
            }
        }
        return callers;
    }

    /**
     * {@code method}가 쓰기 트랜잭션 안에서 도는가. 어노테이션이 판정을 주지 않으면 호출자로 올라간다 —
     * 인터페이스 메서드를 구현하고 있으면 그 인터페이스 메서드의 호출자도 포함한다. 순환은 통과로 본다
     * (다른 경로가 판정을 준다). {@code path}는 실패 시 설명용.
     */
    private static boolean reachesWriteTransaction(JavaMethod method, Set<JavaMethod> visited, List<String> path) {
        if (!visited.add(method)) {
            return true;
        }
        path.add(method.getOwner().getSimpleName() + "." + method.getName());

        Optional<Transactional> transactional = transactionalOf(method);
        if (transactional.isPresent()) {
            switch (transactional.get().propagation()) {
                case REQUIRED, REQUIRES_NEW, NESTED -> {
                    if (transactional.get().readOnly()) {
                        path.set(path.size() - 1, path.get(path.size() - 1) + " [readOnly = true]");
                        return false;
                    }
                    return true;
                }
                case NOT_SUPPORTED, NEVER -> {
                    path.set(path.size() - 1, path.get(path.size() - 1) + " [트랜잭션 밖]");
                    return false;
                }
                case MANDATORY, SUPPORTS -> { /* 호출자 트랜잭션을 쓴다 — 아래로 */ }
            }
        }

        Set<JavaMethod> targets = new LinkedHashSet<>();
        targets.add(method);
        targets.addAll(interfaceMethodsOf(method));
        Set<JavaMethod> callers = callersOf(targets);
        if (callers.isEmpty()) {
            path.set(path.size() - 1, path.get(path.size() - 1) + " [트랜잭션 어노테이션 없음 · 호출자 없음]");
            return false;
        }
        for (JavaMethod caller : callers) {
            List<String> branch = new ArrayList<>(path);
            if (!reachesWriteTransaction(caller, visited, branch)) {
                path.clear();
                path.addAll(branch);
                return false;
            }
        }
        return true;
    }

    // ── 어노테이션 해석 ──────────────────────────────────────────────────

    /**
     * 이 메서드에 실제로 적용되는 {@code @Transactional}. 메서드 레벨이 클래스 레벨을 덮는다 — Spring의 규칙과
     * 같다.
     *
     * <p><b>public이 아니면 없는 것으로 본다.</b> 트랜잭션은 프록시가 거는데 프록시는 public 메서드의 외부 호출에만
     * 끼어든다 — private 헬퍼는 어노테이션이 붙어 있든 클래스에 붙어 있든 <b>호출자의 트랜잭션</b>에서 돈다.
     * 클래스 레벨 {@code readOnly = true} 서비스의 private {@code sendInvitationMail}이 {@code create}(쓰기)에서
     * 불리면 쓰기 트랜잭션이다. 여기서 클래스 레벨을 적용하면 그 경로가 전부 오탐이 된다.
     */
    private static Optional<Transactional> transactionalOf(JavaMethod method) {
        if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
            return Optional.empty();
        }
        return method.tryGetAnnotationOfType(Transactional.class)
                .or(() -> method.getOwner().tryGetAnnotationOfType(Transactional.class));
    }

    private static Propagation propagationOf(JavaMethod method) {
        return transactionalOf(method).map(Transactional::propagation).orElse(null);
    }

    /** {@code method}가 구현(오버라이드)하는 인터페이스 메서드 — 이름·파라미터 타입이 같은 것. */
    private static Set<JavaMethod> interfaceMethodsOf(JavaMethod method) {
        Set<JavaMethod> found = new LinkedHashSet<>();
        String[] parameterTypes = method.getRawParameterTypes().stream()
                .map(JavaClass::getName)
                .toArray(String[]::new);
        for (JavaClass iface : method.getOwner().getAllRawInterfaces()) {
            iface.tryGetMethod(method.getName(), parameterTypes).ifPresent(found::add);
        }
        return found;
    }
}
