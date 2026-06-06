# Gate 6 — DevSecOps · Décision (LoyerTracker)

> Frontière entre la **phase 07 (DevSecOps)** et la **phase 08 (Développement industrialisé)**.
> Recommandation préparée le **2026-06-06** à l'issue des étapes 01→09. Décision finale = ratification PO.

## Conditions d'entrée
- [x] Gate 5 statué **Go**.
- [x] Backlog & sprint 1 prêts (`docs/cgpa/06-planification-agile/`).

## Livrables obligatoires
- [x] **Pipeline CI/CD documenté et opérationnel** — `.github/workflows/ci.yml` (4 jobs), vert sur `main` (run `27057201589`). Doc : `plan-implementation.md`.
- [x] **`checklist-devops.md`** renseignée.
- [x] **`checklist-securite.md`** renseignée.

## Critères de validation
| Critère | Statut | Preuve |
|---------|--------|--------|
| CI : build + tests + quality gate automatisés | ✅ | `mvn verify` (14 tests + JaCoCo 96 % bloquant) ; `ng build`+`ng test` ; sur push/PR |
| Sécurité intégrée : SAST, SCA, scan secrets, scan d'images | 🟠 | SCA (Trivy fs+image) ✅, secrets (Gitleaks) ✅, image (Trivy) ✅ — **SAST absent (R1)** |
| Environnements `dev` / `staging` / `prod` configurés | 🟠 | `dev` (docker compose) ✅ ; staging/prod **R2** |
| Gestion des secrets hors dépôt | ✅ | Étape 08 — 0 secret (Gitleaks dépôt+historique), `.env` gitignoré, Actions Secrets |
| Authentification/autorisation (Keycloak) opérationnelle | ✅ | Realm versionné, OIDC PKCE, resource-server JWT, RBAC+RLS (validé étapes 04/05/06) |
| Supervision / logs de base en place | ✅ | Logs structurés JSON ECS + access log JSON Nginx + Actuator health/info (étape 09) |

**Aucun risque bloquant** : contrôles de sécurité automatisés présents ; aucun secret dans le dépôt ; pipeline reproductible. L'absence de CD n'est pas un déploiement manuel « non maîtrisé » mais un périmètre volontairement repoussé (le Gate 6 ouvre le développement, pas la mise en prod).

## Score de maturité — 16/20

| Axe | Note | Justification |
|-----|------|---------------|
| Complétude | **3**/4 | Étapes 01→10 livrées, stack dev complète et reproductible. CD / registry / observabilité avancée hors périmètre MVP (R2–R4). |
| Qualité | **3**/4 | CI build+tests (unitaires + intégration Testcontainers), quality gate couverture bloquant. Manque gate lint/format (R1). |
| Sécurité | **4**/4 | SCA + scan secrets + scan image bloquants, RLS+FORCE, moindre privilège, TLS/HSTS, API non-root, **remédiation CVE démontrée** (Boot 3.5, Angular 19). SAST (R1) = principale lacune mais posture nettement au-dessus du socle MVP. |
| Traçabilité | **3**/4 | Stories → étapes → critères ; 8 ADR ; décisions PO & écarts consignés ; commits conventionnels. OpenAPI en phase 08. |
| Automatisation | **3**/4 | CI intégrale (4 jobs, gates) sur push/PR ; `docker compose up` reproductible ; build images automatisé. CD non automatisée (R2). |

Seuil CGPA : **≥ 14/20 recommandé** → **atteint (16/20)**.

## Réserves / actions correctives (datées)
| # | Réserve | Échéance recommandée |
|---|---------|----------------------|
| **R1** | Intégrer un **SAST** à la CI (CodeQL ou Semgrep) + gate lint/format (ESLint/Spotless). | Avant fin **sprint 1** (≤ 2026-06-20) |
| **R2** | Définir/provisionner **staging** & **prod** (IaC), **CD** automatisée vers staging + déploiement prod approuvé, stratégie de déploiement + procédure de rollback. | Avant la 1ʳᵉ mise en **staging** |
| **R3** | **Publier** les images dans un registry (tag + push, signature/provenance souhaitable). | Avec R2 |
| **R4** | **Centraliser** logs (ELK/Loki) + **métriques** (Micrometer/Prometheus + Grafana) + **alerting**. | Avant **prod** |
| **R5** | Image **web Nginx non-root** (`nginx-unprivileged`), **chiffrement au repos**, **sauvegardes** + restauration testée. | Avant **prod** |
| **R6** | Migrer vers l'API fonctionnelle **keycloak-angular** (retrait du `KeycloakService` déprécié) et **valider le flux OIDC/PKCE en runtime** contre le realm. | Lors de **US-10** (câblage auth) |

## Décision
- Score de maturité : **16 / 20**
- Recommandation : **✅ Go** (avec réserves R1→R6)
- Décision PO : ☑ ✅ **Go** ☐ 🟠 Go sous réserve ☐ ⛔ No Go
- **Réserves retenues / dates fermes** (toutes acceptées, suivies en phase 08) :
  - **R1** — SAST + gate lint : **≤ 2026-06-20** (fin sprint 1).
  - **R2** — envs staging/prod + CD + rollback : **avant la 1ʳᵉ mise en staging** (jalon, hors MVP).
  - **R3** — publication registry : **avec R2**.
  - **R4** — logs centralisés + métriques + alerting : **avant prod**.
  - **R5** — Nginx non-root + chiffrement au repos + sauvegardes : **avant prod**.
  - **R6** — migration API fonctionnelle keycloak-angular + validation runtime OIDC/PKCE : **lors de US-10**.
- Date & responsable : **2026-06-06** — PO **jptshilombo@gmail.com** (jordan).

> ✅ **Gate 6 statué Go.** Le verrou de la phase 07 est levé : la **phase 08 (Développement)** est ouverte. Les réserves R1→R6 sont reportées et suivies dans le backlog (R1 dans le sprint 1 ; R2→R5 avant mise en production ; R6 avec US-10).

---
*Livrable CGPA v1.0 — Gate 6 (DevSecOps). Réf. : `setup-cgpa/docs/cgpa/gates/gate-6-devsecops.md`.*
