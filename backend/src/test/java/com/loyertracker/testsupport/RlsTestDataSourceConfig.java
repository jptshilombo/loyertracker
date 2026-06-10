package com.loyertracker.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Datasource <strong>admin</strong> (superutilisateur) pour le harnais des tests d'intégration RLS.
 *
 * <p>Depuis la conversion au double datasource, le datasource <em>applicatif</em> (primaire,
 * {@code spring.datasource.*}) se connecte sous le rôle restreint {@code loyertracker_api}
 * (NOSUPERUSER NOBYPASSRLS) : la RLS {@code FORCE} est donc réellement exercée sur le chemin
 * applicatif, comme en production. Mais le harnais de test doit pouvoir contourner la RLS pour
 * préparer/nettoyer la base ({@code TRUNCATE … RESTART IDENTITY}, inserts bas-niveau hors tenant)
 * — opérations que {@code loyertracker_api} n'a pas le droit d'exécuter.</p>
 *
 * <p>On n'expose volontairement <strong>aucun</strong> bean de type {@code DataSource} : cela
 * désactiverait l'auto-configuration du datasource applicatif primaire. On fournit uniquement un
 * {@link JdbcTemplate} nommé {@code admin}, construit sur un {@link DriverManagerDataSource}
 * pointant sur la même URL (conteneur Testcontainers, dynamique) avec les identifiants admin
 * (= ceux de Flyway, {@code spring.flyway.user/password}). À injecter via
 * {@code @Autowired @Qualifier("admin") JdbcTemplate}.</p>
 */
@TestConfiguration
public class RlsTestDataSourceConfig {

    @Bean("admin")
    JdbcTemplate adminJdbc(Environment env) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(env.getRequiredProperty("spring.datasource.url"));
        dataSource.setUsername(env.getProperty("spring.flyway.user", "test"));
        dataSource.setPassword(env.getProperty("spring.flyway.password", "test"));
        return new JdbcTemplate(dataSource);
    }
}
