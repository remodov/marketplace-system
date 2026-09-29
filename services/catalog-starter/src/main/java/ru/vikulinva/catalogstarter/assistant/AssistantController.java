package ru.vikulinva.catalogstarter.assistant;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/assistant")
public class AssistantController {

    private final RagAssistant assistant;
    private final ShopKnowledge knowledge;

    public AssistantController(RagAssistant assistant, ShopKnowledge knowledge) {
        this.assistant = assistant;
        this.knowledge = knowledge;
    }

    public record AskRequest(@NotBlank String question) {
    }

    @PostMapping("/ask")
    public RagAssistant.Answer ask(@Valid @RequestBody AskRequest request) {
        return assistant.ask(request.question());
    }

    /* Пересборка базы вынесена в ручку, а не повешена на старт приложения:
       на старте это лишние секунды, а в тестах — гонка с наполнением каталога. */
    @PostMapping("/reindex")
    public void reindex() {
        knowledge.reindex();
    }
}
