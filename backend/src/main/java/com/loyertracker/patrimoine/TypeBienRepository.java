package com.loyertracker.patrimoine;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TypeBienRepository extends JpaRepository<TypeBien, String> {

    List<TypeBien> findAllByOrderByLibelleAsc();
}
