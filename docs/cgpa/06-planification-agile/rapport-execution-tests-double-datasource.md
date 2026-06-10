# Rapport d'exécution — Tests d'intégration au double datasource (fidélité RLS)

- **Type :** suivi technique / dette de test (ADR-01)
- **Branche :** `chore/tests-double-datasource-rls` (depuis `main` @ `0ae7e8f`)
- **Niveau du Plan d'Exécution :** 3
- **Agent :** Claude Opus 4.8 (1M context)
- **Date :** 2026-06-10
- **Statut :** ✅ Exécuté, `mvn verify` vert (55 tests), prêt pour revue/merge (décision PO)

## 1. Objet

Faire tourner l'application, dans les tests d'intégration `@SpringBootTest`, sous le rôle restreint
**`loyertracker_api` (NOSUPERUSER NOBYPASSRLS)** — comme en production — au lieu du superutilisateur
Testcontainers qui **contournait la RLS `FORCE`**. La 2ᵉ couche de défense (ADR-01) est désormais
**réellement exercée** sur tout le chemin applicatif, flux API authentifié inclus (ce que le smoke
test du 2026-06-08 ne couvrait pas).

## 2. Conception retenue (double datasource)

- **Datasource applicatif (primaire, `spring.datasource.*`)** = `loyertracker_api` (identifiants
  statiques dans `application.properties` de test ; seule l'URL reste dynamique par conteneur).
- **Flyway** = superutilisateur du conteneur (admin), seul capable de `CREATE ROLE` (V5), migrant
  **avant** la 1ʳᵉ connexion du pool applicatif (ordre garanti Flyway → EMF Hibernate, identique prod).
- **Harnais de test (seed/cleanup)** = `JdbcTemplate` **admin** dédié (`RlsTestDataSourceConfig`,
  `@Qualifier("admin")`), pour `TRUNCATE … RESTART IDENTITY` et inserts bas-niveau hors tenant —
  opérations interdites à `loyertracker_api`. Aucun bean de type `DataSource` n'est exposé (pour ne
  pas désactiver l'auto-configuration du datasource applicatif primaire).
- Le **seed « métier »** continue de passer par l'**API** sous `loyertracker_api` : le chemin réaliste.

## 3. Livrables (diff)

| Fichier | Nature | Détail |
|---|---|---|
| `test/.../testsupport/RlsTestDataSourceConfig.java` | NEW | `@TestConfiguration` exposant `@Bean("admin") JdbcTemplate` (DriverManagerDataSource, URL dynamique + creds admin). |
| `test/resources/application.properties` | MOD | `spring.datasource.username/password` = `loyertracker_api`/placeholder ; Flyway reste admin. |
| `BailleurInscriptionIntegrationTest` | MOD | Retrait des overrides creds + **test de verrou** `applicationConnecteeSousRoleRestreintNonBypassRls` (assert `current_user = loyertracker_api`, non BYPASSRLS, non superuser). |
| `InvitationGenerationIntegrationTest` | MOD | Retrait des overrides creds. |
| `InvitationAcceptationIntegrationTest`, `S02…`, `S03…`, `S04Honoraires…`, `S04AlertesAudit…`, `AuthorizationServiceIntegrationTest` | MOD | Retrait des overrides creds ; `@Import(RlsTestDataSourceConfig.class)` ; `JdbcTemplate` de seed/cleanup basculé `@Qualifier("admin")`. |

- **Hors périmètre (inchangés)** : `SchemaMigrationTest` (RLS déjà prouvée hors Spring) et
  `SecurityIntegrationTest` (profil `test` sans BDD).
- **Aucune modification applicative ni de migration** : le code (ex. `InscriptionService`,
  auto-autorisant via GUC `WITH CHECK`) et les grants (V5 : DML + EXECUTE + privilèges par défaut)
  avaient déjà été écrits en anticipant le rôle restreint. **Aucun `V11` requis.**

## 4. Investigation & triage

Conformément au défaut C, `mvn verify` a été lancé après conversion pour révéler d'éventuels trous
(GUC non positionné, grant/policy manquant). **Aucun échec révélé** : tous les chemins (inscription
bootstrap, invitation/acceptation, biens/baux/affectations, paiements/garanties, honoraires, alertes
multi-bailleur via `SECURITY DEFINER`, audit, ReBAC) fonctionnent sous `loyertracker_api`. Le triage
n'a donc nécessité aucun correctif.

## 5. Tests & vérification

- `mvn verify` → **BUILD SUCCESS**, **55 tests** (54 + 1 verrou RLS), 0 échec ; Spotless + JaCoCo OK.
- Verrou de fidélité : `current_user = loyertracker_api`, `rolbypassrls = false`, `rolsuper = false`
  — empêche toute régression silencieuse vers le superutilisateur.

## 6. Conformité CGPA & suite

- Plan d'Exécution approuvé (défauts A–D) avant codage.
- Risque §13 « Comportement runtime sous le rôle restreint non vérifié end-to-end » : **passe à
  Fermé** côté tests (RLS prouvée bout-en-bout via l'API authentifiée). Reste, distinct : smoke test
  runtime sur la **stack complète** (Keycloak + Nginx) — suivi séparé.
- Synchro `project-state.md` post-merge (PR de doc séparée).
