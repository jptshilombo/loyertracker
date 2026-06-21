package com.loyertracker.patrimoine;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TypeBienService {

    private final TypeBienRepository typesBiens;

    public TypeBienService(TypeBienRepository typesBiens) {
        this.typesBiens = typesBiens;
    }

    @Transactional(readOnly = true)
    public List<TypeBienDto> lister() {
        return typesBiens.findAllByOrderByLibelleAsc().stream()
                .map(TypeBienDto::from)
                .toList();
    }
}
