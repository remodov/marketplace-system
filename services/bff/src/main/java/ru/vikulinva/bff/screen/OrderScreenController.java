package ru.vikulinva.bff.screen;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/screens")
public class OrderScreenController {

    private final OrderScreenService service;

    public OrderScreenController(OrderScreenService service) {
        this.service = service;
    }

    @GetMapping("/order/{orderId}")
    public OrderScreen orderScreen(@PathVariable UUID orderId) {
        return service.assemble(orderId);
    }
}
