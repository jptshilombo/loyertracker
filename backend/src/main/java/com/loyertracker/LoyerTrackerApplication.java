package com.loyertracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée de l'API LoyerTracker.
 *
 * <p>Squelette de la Phase 07 / étape 04 : resource-server JWT stateless. La persistance
 * (JPA + Flyway) et les modules métier sont introduits aux étapes suivantes.</p>
 */
@SpringBootApplication
public class LoyerTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoyerTrackerApplication.class, args);
    }
}
