package com.twojo.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 클래스 레벨 {@code @Transactional(readOnly = true)} 서비스의 <b>쓰기 메서드</b>에
 * 메서드 레벨 {@code @Transactional}이 붙어 있는가 (13 §3).
 *
 * <p><b>왜 테스트로 잡는가</b> — 빠뜨려도 컴파일이 되고 테스트가 통과하고 런타임도 조용하다.
 * 읽기 전용 트랜잭션에서 돌면 Hibernate가 flush를 건너뛰어 <b>변경이 예외도 로그도 없이 버려진다.</b>
 * 사람이 리뷰에서 잡아야 하는 종류인데 대상 클래스가 이미 24개라, 다음에 추가되는 메서드에서 샌다
 * (PR #154 「리뷰어에게 2」에서 C가 제기 · #136 리뷰에서 같은 경고).
 *
 * <p><b>왜 이름으로 가리는가</b> — "리포지터리 {@code save}를 부르는가"로 보는 편이 정확해 보이지만,
 * JPA는 엔티티를 고치기만 해도 더티 체킹으로 쓰기가 된다({@code deal.markWon()}). 호출만 보면
 * 그쪽이 통째로 빠진다. 그래서 이름 규약을 정본으로 두고, 새 동사가 생기면
 * {@link #WRITE_PREFIXES}에 추가한다.
 *
 * <p><b>어노테이션이 붙었는지만 보지 않는다</b> — {@code @Transactional(readOnly = true)}를 복사해
 * 붙이면 어노테이션은 있는데 여전히 읽기 전용이다. {@code readOnly = false}까지 확인한다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReadOnlyServiceWriteMethodTest {

    /**
     * 13 §3 — 쓰기 메서드의 이름 규약.
     *
     * <p>목록이 낡으면 새 동사가 조용히 빠지므로 {@link #쓰기_메서드_이름이_전부_이_목록에_있다}가
     * 목록을 현행으로 붙잡는다 — 메서드 레벨 {@code @Transactional}을 붙인 메서드가 여기에
     * 없는 이름이면 그 테스트가 먼저 실패해서, 동사를 추가하라고 말한다.
     */
    private static final List<String> WRITE_PREFIXES = List.of(
            "accept", "add", "advance", "approve", "cancel", "change", "convert", "create",
            "deactivate", "delete", "discontinue", "expire", "invite", "issue", "lose", "mark",
            "promote", "reactivate", "reassign", "record", "reject", "reopen", "replace",
            "resend", "revert", "schedule", "send", "set", "suspend", "update", "withdraw");

    private static final DescribedPredicate<JavaMethod> IN_READ_ONLY_SERVICE =
            new DescribedPredicate<>("클래스 레벨 @Transactional(readOnly = true)인 @Service의 public 메서드") {
                @Override
                public boolean test(JavaMethod method) {
                    if (!method.getModifiers().contains(JavaModifier.PUBLIC)
                            || method.getModifiers().contains(JavaModifier.STATIC)) {
                        return false;
                    }
                    var owner = method.getOwner();
                    return owner.isAnnotatedWith(Service.class)
                            && owner.tryGetAnnotationOfType(Transactional.class)
                                    .map(Transactional::readOnly)
                                    .orElse(false);
                }
            };

    private static final DescribedPredicate<JavaMethod> NAMED_LIKE_A_WRITE =
            new DescribedPredicate<>("이름이 쓰기 동사로 시작한다 " + WRITE_PREFIXES) {
                @Override
                public boolean test(JavaMethod method) {
                    String name = method.getName();
                    return WRITE_PREFIXES.stream().anyMatch(prefix -> name.equals(prefix)
                            || (name.startsWith(prefix)
                                    && name.length() > prefix.length()
                                    && Character.isUpperCase(name.charAt(prefix.length()))));
                }
            };

    /**
     * 메서드 레벨 어노테이션이 <b>쓰기</b> 트랜잭션을 여는가.
     *
     * <p>붙어 있는지만 보면 {@code @Transactional(readOnly = true)}를 복사해 붙인 쓰기 메서드가
     * 통과한다 — 어노테이션은 있는데 여전히 읽기 전용이라 변경은 그대로 버려진다.
     * 이 저장소에도 메서드 레벨 {@code readOnly = true} 선례가 있다({@code LoginAttemptService}).
     */
    private static boolean opensWriteTransaction(JavaMethod method) {
        return method.tryGetAnnotationOfType(Transactional.class)
                .map(annotation -> !annotation.readOnly())
                .orElse(false);
    }

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.twojo");
    }

    @Test
    void 클래스_readOnly_서비스의_쓰기_메서드는_메서드_레벨_Transactional을_가진다() {
        ArchRule rule = ArchRuleDefinition.methods()
                .that(IN_READ_ONLY_SERVICE)
                .and(NAMED_LIKE_A_WRITE)
                .should(haveMethodLevelTransactional())
                .because("읽기 전용 트랜잭션에서 돌면 변경이 예외 없이 버려진다 (13 §3)");

        rule.check(productionClasses);
    }

    /**
     * 규칙이 실제로 무언가를 보고 있는가. 술어가 어긋나 대상이 0건이 되면 위 테스트는 공짜로 통과하고,
     * 그때부터 아무것도 지키지 않으면서 초록불만 남는다.
     */
    @Test
    void 규칙이_검사하는_메서드가_실제로_있다() {
        var checked = productionClasses.stream()
                .flatMap(clazz -> clazz.getMethods().stream())
                .filter(IN_READ_ONLY_SERVICE)
                .filter(NAMED_LIKE_A_WRITE)
                .map(JavaMethod::getFullName)
                .toList();

        assertThat(checked).as("클래스 readOnly 서비스의 쓰기 메서드").isNotEmpty();
    }

    /**
     * 이름 목록이 현행인가 — 메서드 레벨 {@code @Transactional}을 붙였다는 것은 쓰기라는 뜻이므로,
     * 그 이름은 {@link #WRITE_PREFIXES}에 있어야 한다.
     *
     * <p>이 테스트가 없으면 목록이 조용히 낡는다. 새 쓰기 메서드를 {@code frobnicate}로 짓고
     * 어노테이션을 붙이면 위 규칙은 그 메서드를 아예 보지 않아 초록불이고, 그다음 사람이
     * 같은 이름으로 어노테이션을 빠뜨려도 아무도 모른다. 여기서 먼저 실패시켜 동사를 등록하게 한다.
     */
    @Test
    void 쓰기_메서드_이름이_전부_이_목록에_있다() {
        var unlisted = productionClasses.stream()
                .flatMap(clazz -> clazz.getMethods().stream())
                .filter(IN_READ_ONLY_SERVICE)
                .filter(ReadOnlyServiceWriteMethodTest::opensWriteTransaction)
                .filter(method -> !NAMED_LIKE_A_WRITE.test(method))
                .map(JavaMethod::getFullName)
                .toList();

        assertThat(unlisted)
                .as("메서드 레벨 @Transactional인데 이름이 WRITE_PREFIXES에 없다 — 동사를 목록에 추가할 것")
                .isEmpty();
    }

    private static ArchCondition<JavaMethod> haveMethodLevelTransactional() {
        return new ArchCondition<>("메서드 레벨 @Transactional을 가진다") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (opensWriteTransaction(method)) {
                    return;
                }
                String reason = method.isAnnotatedWith(Transactional.class)
                        ? "메서드 레벨 @Transactional이 readOnly = true다 — 어노테이션은 있지만 여전히 읽기 전용이다"
                        : "메서드 레벨 @Transactional이 없다";
                events.add(SimpleConditionEvent.violated(method, method.getFullName()
                        + " — 클래스가 @Transactional(readOnly = true)인데 " + reason
                        + ". 변경이 조용히 버려진다 (13 §3)"));
            }
        };
    }
}
