package com.twojo.global;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.hamcrest.Matchers;

/**
 * Prometheus 로 나가는 지표가 대시보드가 기대하는 모양인가.
 *
 * <p>이 계열의 결함은 앱을 멀쩡해 보이게 두고 관측만 조용히 망가뜨린다.
 * 실제로 그랬다 — 지표가 통째로 사라진 채(#221) 19시간 동안 아무도 몰랐다.
 * 그래서 "나오는가"와 "어떤 라벨로 나오는가"를 둘 다 잡아둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MetricsExposureTest {

    @Autowired private MockMvc mockMvc;

    /** Boot 4 부터 지표 내보내기가 옵트인이다. 꺼지면 여기가 404 로 바뀐다 (#221) */
    @Test
    void 지표_엔드포인트가_열려_있다() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("jvm_memory_used_bytes")));
    }

    /**
     * 커뮤니티 대시보드(JVM 4701 등)가 application 라벨로 앱을 고른다.
     * 이 라벨이 빠지면 패널이 전부 빈 화면이 되는데, 지표는 정상적으로
     * 나오므로 어디가 잘못됐는지 알아채기 어렵다.
     */
    @Test
    void 모든_지표에_application_라벨이_붙는다() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        Matchers.containsString("application=\"twojo-backend\"")));
    }
}
