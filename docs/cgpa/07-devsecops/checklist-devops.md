# Checklist DevOps / CI-CD — LoyerTracker

> Renseignée au **Gate 6 (DevSecOps)** — état au 2026-06-06 (issue des étapes 01→09).
> Mise à jour documentaire CGPA v3.0.1 le **2026-06-07** : réserve **R1 clôturée** après ajout de CodeQL, Spotless et ESLint. R6 validée partiellement en runtime : OIDC/PKCE et API protégée OK ; Admin API gestionnaire KO.
> Légende : `[x]` fait · `[ ]` non fait. Statut : ✅ couvert · 🟠 partiel · ⏭️ différé (hors périmètre MVP, réserve datée au Gate 6).

## 1. Gestion de versions
- [x] ✅ Tout est versionné (code, infra, configs, docs) — mono-dépôt git : `backend/`, `frontend/`, `infra/`, `docs/cgpa/`, workflows CI.
- [x] ✅ Stratégie de branches définie — `CONTRIBUTING.md` §Stratégie de branches (`main` protégée / `develop` / `feat/<us-id>`). 🟠 *Déviation assumée* : les étapes 06→10 ont été poussées directement sur `main` (dev solo, décision PO « Pousser sur main ») ; le flux PR `feat/* → develop → main` s'appliquera dès l'arrivée de contributeurs / phase 7.
- [x] ✅ Convention de commits / PR avec revue — commits **Conventional Commits** (`feat`/`ci`/`docs`/`fix` + scope + US). 🟠 Revue de PR humaine non encore appliquée (solo) ; **revue de sécurité automatisée** active à chaque push (plugin de revue + Gitleaks/Trivy en CI).

## 2. Intégration continue (CI)
- [x] ✅ Build automatisé à chaque push/PR — `.github/workflows/ci.yml` (`on: push ["**"] + pull_request`), 4 jobs, concurrency.
- [x] ✅ Tests unitaires & d'intégration exécutés en CI — backend `mvn verify` (32 tests dont migration Flyway via **Testcontainers**) ; frontend `ng test` (Chrome headless).
- [x] ✅ Quality gate bloquant — **couverture JaCoCo** ≥ 80 % sur `com.loyertracker.securite`, `exit-code` bloquant ; **Spotless Maven** et **ESLint Angular** exécutés en CI. Réserve **R1 traitée** sur le volet lint/format.
- [x] ✅ Scans de sécurité (SAST/SCA/secrets) intégrés — **CodeQL** Java/TypeScript, **SCA** (Trivy fs npm + Trivy image + OWASP DC informatif), **secrets** (Gitleaks dépôt+historique), **scan d'image** (Trivy `vuln,secret`). Réserve **R1 traitée** sur le volet SAST.

## 3. Packaging
- [x] ✅ Build d'images Docker reproductible — Dockerfiles multi-stage (API JRE Alpine, Web Nginx Alpine), job `docker-build` (tag `sha-<court>`).
- [ ] ⏭️ Images versionnées et publiées dans un registry — images **buildées et validées** en CI mais **non publiées** (push registry hors périmètre MVP) — réserve R3.
- [x] ✅ Configuration externalisée (12-factor) — variables d'environnement (`.env.example`, `${VAR}`), logs sur stdout, aucun secret embarqué.

## 4. Déploiement continu (CD)
- [ ] 🟠 Environnements distincts `dev` / `staging` / `prod` — seul **dev** existe (`docker compose`) ; staging/prod à provisionner — réserve R2.
- [ ] ⏭️ Déploiement automatisé vers `staging` — non (pas d'environnement cible) — réserve R2.
- [ ] ⏭️ Déploiement `prod` contrôlé (approbation) — non — réserve R2.
- [ ] ⏭️ Stratégie de déploiement définie (rolling/blue-green/canary) — non — réserve R2.
> *La CD est délibérément hors périmètre du MVP : le Gate 6 ouvre la phase 7 (développement) ; aucun déploiement n'est requis pour démarrer le build des features.*

## 5. Infrastructure
- [x] 🟠 Infrastructure as Code (si applicable) — `docker-compose.yml` + `infra/nginx/nginx.conf` + realm Keycloak versionnés (IaC du poste dev). ⏭️ Pas d'IaC cloud (Terraform/…) — lié à R2.
- [x] ✅ Secrets gérés hors dépôt — étape 08 (`.env` gitignoré, GitHub Actions Secrets, 0 secret dans l'historique — Gitleaks).
- [ ] ⏭️ Sauvegardes automatisées — pas de base de prod ; politique de sauvegarde à définir — réserve R5.

## 6. Observabilité
- [x] 🟠 Logs centralisés — **journalisation structurée JSON ECS** (backend) + access log JSON (Nginx) sur stdout, **prêts pour l'agrégation**. ⏭️ Agrégateur (ELK/Loki) non déployé — réserve R4.
- [ ] ⏭️ Métriques & dashboards — Actuator présent ; pas d'export Micrometer/Prometheus ni Grafana — réserve R4.
- [ ] ⏭️ Alerting configuré — non — réserve R4.

## 7. Réversibilité
- [x] 🟠 Plan de rollback — migrations Flyway versionnées (V1) + images taguées par SHA (retour à un tag antérieur possible). ⏭️ Procédure formelle non rédigée — réserve R2.
- [ ] ⏭️ Procédure de restauration testée — non (pas de prod) — réserve R5.

## 8. État des réserves Gate 6 après resynchronisation CGPA v3.0.1

| Réserve | Statut au 2026-06-07 | Commentaire |
|---------|----------------------|-------------|
| R1 — SAST + lint/format | ✅ Clôturée techniquement | CodeQL Java/TypeScript, Spotless Maven et ESLint Angular présents en CI. |
| R2 — staging/prod + CD + rollback | ⏭️ Ouverte | À traiter avant première mise en staging. |
| R3 — registry images | ⏭️ Ouverte | À traiter avec R2. |
| R4 — logs centralisés, métriques, alerting | ⏭️ Ouverte | À traiter avant production. |
| R5 — Nginx non-root, chiffrement, sauvegardes | ⏭️ Ouverte | À traiter avant production. |
| R6 — validation runtime Keycloak | 🟠 Partielle | Runtime OIDC/PKCE validé (`aud=loyertracker-api`, rôle `BAILLEUR`, API protégée OK) ; Admin API gestionnaire KO en 401 car client `loyertracker-api` bearer-only sans service account et variables Admin API absentes du conteneur `api`. |

---
*Checklist CGPA v3.0.1 — Phase 6 (DevSecOps). Réserves R1→R6 détaillées dans `gate-6-decision.md`.*
