package ru.vikulinva.catalogstarter.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentLoop agent;

    public AgentController(AgentLoop agent) {
        this.agent = agent;
    }

    /**
     * @param confirmed человек посмотрел на предложенный изменяющий вызов и
     *                  разрешил его. Флаг приходит отдельным полем, а не внутри
     *                  задачи: иначе его сможет выставить текст пользователя.
     */
    public record RunRequest(@NotBlank String task, boolean confirmed) {
    }

    @PostMapping("/run")
    public AgentLoop.Result run(@Valid @RequestBody RunRequest request) {
        return agent.run(request.task(), request.confirmed());
    }
}
