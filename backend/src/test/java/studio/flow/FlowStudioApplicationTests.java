package studio.flow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FlowStudioApplicationTests {
  @Autowired private MockMvc mockMvc;

  @Test
  void healthReportsOk() throws Exception {
    mockMvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  void editRejectsUnsupportedVideoType() throws Exception {
    MockMultipartFile video =
        new MockMultipartFile("video", "input.txt", "text/plain", "bad".getBytes());
    MockMultipartFile mask =
        new MockMultipartFile("mask", "mask.png", "image/png", new byte[] {0, 1, 2});

    mockMvc
        .perform(
            multipart("/api/tasks/edit")
                .file(video)
                .file(mask)
                .param("targetPrompt", "change the selected area")
                .param("targetWord", "rose"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorMessage").value("video file type is not supported."));
  }
}
