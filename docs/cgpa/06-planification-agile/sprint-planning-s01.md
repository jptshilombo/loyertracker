# Sprint Planning — LoyerTracker — Sprint 01

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker |
| Sprint | 01 |
| Période | 2026-06-08 → 2026-06-19 (2 semaines) |
| Phase | 06 / 07 — Agile & développement |
| Responsable | jptshilombo@gmail.com |

---

## 1. Objectif du sprint

> **Socle technique opérationnel + authentification OIDC fonctionnelle.**
>
> À la fin de ce sprint, un développeur peut `docker compose up`, obtenir une stack complète (Nginx · API · Keycloak · PostgreSQL), s'authentifier en tant que bailleur via OIDC, et disposer du schéma de base complet avec les contraintes d'intégrité actées.

---

## 2. Capacité

| Indicateur | Valeur |
|-----------|--------|
| Équipe | dev solo |
| Durée | 10 jours effectifs |
| Vélocité cible | **20 story points** |
| Engagement sprint | **20 pts** |

---

## 3. Stories engagées

| ID | Story (résumé) | Pts | Critères d'acceptation clés |
|----|---------------|-----|-----------------------------|
| US-01 | Stack Docker Compose (nginx · api · keycloak · postgres) en une commande | 5 | `docker compose up` → 4 services up ; nginx 443 répond ; `/api/actuator/health` OK |
| US-02 | Realm Keycloak configuré (rôles `BAILLEUR`/`GESTIONNAIRE`, clients SPA+backend) | 3 | JWT bailleur de test obtenu via PKCE ; backend rejette JWT invalide (401) |
| US-03 | Migration Flyway initiale — schéma complet (tables, FK, index uniques partiels, RLS) | 5 | Toutes les tables existent ; index `uq_bail_actif`, `uq_affectation_active`, `uq_paiement_periode`, `uq_honoraire_periode`, `uq_alerte_nonlue` créés ; 2ᵉ run idempotent |
| US-04 | Pipeline CI minimal (build · tests · scan secrets) | 3 | Push → build compilé, tests passants, scan secrets sans résultat |
| US-10 | Inscription bailleur + session OIDC | 3 | Compte `BAILLEUR` créé, session OIDC ouverte, dashboard accessible |
| **Total** | | **19 pts** | |

> US-10 est ajouté pour utiliser et valider l'intégration Keycloak dès S01 (vérifie US-01+02+03 end-to-end). Un point de marge conservé sur la vélocité cible.

---

## 4. Tâches techniques

| Tâche | Story liée | Estimation |
|-------|-----------|-----------|
| Initialiser le projet Spring Boot (deps : Spring Security, Spring Data JPA, Flyway, Actuator, Keycloak adapter) | US-01 | 0,5 j |
| Initialiser le projet Angular (deps : oidc-client-ts ou keycloak-angular) | US-01 | 0,5 j |
| Rédiger `docker-compose.yml` + `nginx.conf` (routes `/`, `/auth`, `/api`) | US-01 | 0,5 j |
| Configurer terminaison TLS Nginx (certificat auto-signé dev) + en-têtes sécurité | US-01 | 0,5 j |
| Script d'import realm Keycloak (realm.json) — rôles, clients, realm settings | US-02 | 0,5 j |
| Configurer resource-server Spring Security (JWT validation, extraction rôle) | US-02 | 0,5 j |
| Rédiger migration `V1__init_schema.sql` (toutes les tables du MLD) | US-03 | 1 j |
| Activer et tester la RLS PostgreSQL (`bailleur_id` + intercepteur Spring) | US-03 | 0,5 j |
| Configurer GitHub Actions (ou CI cible) : build Maven + Angular, Trivy/gitleaks scan | US-04 | 0,5 j |
| Endpoint `POST /api/bailleurs/inscription` + test d'intégration | US-10 | 0,5 j |
| Flux OIDC Angular (login redirect → token → appel API protégé) | US-10 | 0,5 j |
| **Total estimé** | | **~6,5 j** |

> Marge sprint : ~3,5 j pour imprévus, ajustements et revue.

---

## 5. Definition of Done (rappel)

- [ ] Code revu, ADR respectés (ADR-01 RLS testée, ADR-02 séparation AuthN/AuthZ, ADR-08 Nginx)
- [ ] Tests unitaires + intégration passants
- [ ] 0 secret en clair (scan CI)
- [ ] Migration Flyway versionnée et appliquée
- [ ] Stack déployable via `docker compose up`

---

## 6. Risques du sprint

| Risque | Mitigation |
|--------|------------|
| Complexité de la config Keycloak (PKCE + realm import reproductible) | Prévoir 1 j de marge ; realm.json versionné dans le dépôt (`config/keycloak/`) |
| RLS PostgreSQL — injection contexte tenant (intercepteur Spring) | Valider avec un test d'intégration dès US-03 ; si bloquant > 0,5 j, réduire à discriminant seul et rouvrir en S02 |
| Certificat TLS auto-signé Nginx en dev — browsers bloquants | Utiliser `mkcert` ou `localhost.direct` ; documenter dans README |
| US-10 non terminée (dépend de US-01+02+03) | Si socle prend plus de temps, reporter US-10 en début de S02 sans impact majeur |

---

## 7. Revue & rétrospective (fin de sprint)

*À compléter en fin de sprint S01.*

- Réalisé : —
- Non réalisé : —
- Vélocité effective : — pts
- Améliorations pour S02 : —
