package com.loyertracker.patrimoine;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/types-biens")
public class TypeBienController {

    private final TypeBienService typeBienService;

    public TypeBienController(TypeBienService typeBienService) {
        this.typeBienService = typeBienService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE')")
    public List<TypeBienDto> lister() {
        return typeBienService.lister();
    }
}
