# Contribuer à LoyerTracker

> Ce projet est gouverné par le **CGPA** (`setup-cgpa/`). Le développement applicatif est autorisé depuis le **Gate 4 Go** (verrou de codage levé). Toute contribution respecte la Definition of Done du backlog (`docs/cgpa/06-planification-agile/product-backlog.md`).

## Structure du dépôt

```
loyertracker/
├── backend/      # API Spring Boot (Java 21, Maven) — packages par domaine métier
├── frontend/     # SPA Angular (OIDC/PKCE)
├── infra/        # nginx (reverse proxy) + keycloak (realm versionné)
├── .github/      # pipelines CI/CD
└── docs/         # livrables CGPA (gouvernance — ne pas modifier sans décision consignée)
```

Le découpage backend suit les **modules métier** définis dans le DAT (Phase 05) :
`comptes · biens · baux · affectations · paiements · garanties · honoraires · alertes · audit · batch · securite`.
Chaque module respecte la séparation **Controller → Service → Repository**. Toute la logique de cloisonnement et d'autorisation fine est portée par le **service layer** (jamais le controller seul) — cf. ADR-01 et ADR-02.

## Convention de commits — Conventional Commits

```
<type>(<scope>): <description courte à l'impératif>
```

| Type | Usage |
|------|-------|
| `feat` | nouvelle fonctionnalité |
| `fix` | correction de bug |
| `test` | ajout / modification de tests |
| `docs` | documentation (dont `docs(cgpa)` pour les livrables de gouvernance) |
| `infra` | Docker, Nginx, Keycloak (`infra(docker)`, `infra(nginx)`, `infra(keycloak)`) |
| `ci` | pipeline CI/CD |
| `refactor` | refactorisation sans changement de comportement |
| `chore` | tâches diverses (deps, config) |

Le `scope` est de préférence le **module métier** ou la **user story** concernée (ex. `feat(biens)`, `feat(us-20)`).

## Stratégie de branches

| Branche | Rôle |
|---------|------|
| `main` | code stable et livrable — **protégée** (merge via PR validée) |
| `develop` | intégration continue des features |
| `feat/<us-id>-<slug>` | une branche par user story (ex. `feat/us-01-docker-compose`) |
| `fix/<slug>` | corrections |

**Flux :** `feat/* → develop` (PR + CI verte) ; `develop → main` à la clôture d'un sprint validé.

## Definition of Done (rappel)

Une story est *Terminée* uniquement si :

- [ ] Code revu, conforme au CDC et aux ADR
- [ ] Tests unitaires + d'intégration passants
- [ ] Tests d'autorisation passants (0 accès cross-bailleur / cross-affectation)
- [ ] Aucun secret en clair (scan CI)
- [ ] Migration Flyway versionnée (si schéma impacté)
- [ ] Endpoint(s) documenté(s)
- [ ] Déployable via `docker compose up`

## Secrets

Aucun secret en clair dans le dépôt. Copier `.env.example` vers `.env` (non versionné) pour le dev local. Voir `docs/cgpa/07-devsecops/plan-implementation.md` (Étape 08).

## Référence

- Plan d'implémentation : `docs/cgpa/07-devsecops/plan-implementation.md`
- Backlog & sprints : `docs/cgpa/06-planification-agile/`
- Architecture & ADR : `docs/cgpa/05-architecture-conception/`
