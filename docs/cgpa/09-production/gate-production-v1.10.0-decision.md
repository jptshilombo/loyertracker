# Gate Production — Release `1.10.0` (EP-15, Sprints A+B — Gestion des personnes)

| Champ | Valeur |
|---|---|
| Date | 2026-07-15 |
| Version | `1.10.0` MINOR |
| Candidat | `c9200a51f60d14720f41fc43a762c54fd5ab7917` / tag `sha-c9200a51` |
| Digest API | `sha256:37de87e86dfe99d0483ef6ac1934384e773f858822ab53bbe29432e7d6858db9` |
| Digest Web | `sha256:7ade9816f3844f10d2e8a9f63491380546d6c68370011d38fc04368ee5e51052` |
| Source | `ai-test-server`, `STAGING_DEPLOYED` (`gate-staging-sprints-ab-ep15-decision.md`) |
| Production / rollback | `1.9.0`, `sha-75646d8f` |
| Décision | **GO — `PRODUCTION_READY`** |

## Périmètre

Sprint A EP-15 (Gestionnaire : statut global, profil enrichi, cycle de vie, garde cross-tenant
d'archivage, US-105→108) + Sprint B EP-15 (Locataire : nouvelle entité RLS, US-109→112), mergés
ensemble par le plan d'exécution EP-15 (« pas de promotion isolée du Sprint A avant le Sprint
B »). PR #217 (`c9200a5`, documentaire) est incluse dans le tag candidat sans modifier les
images. `main` HEAD a depuis avancé à `a7f7771` (PR #218, doc Gate Staging) : **diff nul par
rapport à `c9200a5` hors documentation** (`git diff --stat c9200a5 HEAD` : 4 fichiers, tous sous
`docs/`) — le candidat de ce Gate reste exactement `sha-c9200a51`, celui vérifié en Staging.

**Hors périmètre** : Sprint C (bascule `Bail`→`Locataire`, migration V25 non additive) — reste
bloqué tant qu'un cycle de release Production complet du Sprint B n'a pas été observé sans
anomalie (condition actée au plan d'exécution EP-15).

## Checklist CGPA v5.4.1

| Critère | Statut | Preuve |
|---|---|---|
| Identification complète | PASS | EP-15 Sprints A+B, `1.10.0`, tag/digests, Staging vers Production |
| Candidat exact en Staging | PASS | `sha-c9200a51`, Gate Staging GO (`gate-staging-sprints-ab-ep15-decision.md`) |
| `STG-ISOL-01` | PASS | 8 conteneurs `loyertracker-staging-*` + NPM mutualisé intacts avant/après, restart=0, aucune commande Docker globale |
| Smoke / Flyway | PASS | Smoke 62/0 ; Flyway V23+V24 appliquées, 24/24 |
| Vérification fonctionnelle dédiée | PASS | 48/0 sur `/api/gestionnaires` et `/api/locataires` (CRUD, cycle de vie, RBAC/ReBAC, garde cross-tenant d'archivage, RLS 404, doublons, historique) ; les deux correctifs du PR #209 (photo Gestionnaire, `dateCreation` Locataire) reconfirmés en conditions réelles |
| Sécurité | PASS | Revue dédiée (`security-review-sprints-ab-ep15.md`) — fonctions `SECURITY DEFINER` à surface minimale (booléen, paramètres liés, `EXECUTE` restreint), RLS Locataire standard, RBAC/ReBAC Gestionnaire fail-closed, aucun `permitAll` sur les nouveaux endpoints |
| CI / tests | PASS | CI GitHub 7/7 SUCCESS sur `4d8a760` et `c9200a5` (Backend, Frontend, CodeQL java-kotlin+js-ts, Sécurité gitleaks+SCA+Trivy, Packaging Docker) ; `mvn verify` 173/173 |
| SonarQube | PASS | Quality Gate bloquant intégré aux jobs Backend/Frontend (`sonar.qualitygate.wait=true`) — vert sur les deux jobs, aucune régression |
| Migration / rollback | PASS | V23 (colonnes `gestionnaire`) et V24 (table `locataire`) additives — `1.9.0` ignore les nouveaux objets, rollback applicatif seul viable |
| Secrets Production | PASS — sans objet | Aucune nouvelle variable d'environnement/secret introduite (contrairement à `QUITTANCE_HMAC_SECRET` en `1.9.0`) — confirmé par diff des fichiers Compose/`.env.example` |
| Observabilité | PASS | Aucun nouveau dispositif requis pour ce périmètre ; dispositif Production existant inchangé |
| PO / Release Manager | PASS | Plan d'exécution EP-15 respecté (Sprint A+B combinés) ; candidat, digests et rollback identifiés |
| Release notes / changelog | PASS sous condition | `CHANGELOG.md` `[Non publié]` couvre déjà Sprints A+B ; promotion en `[1.10.0]` daté à faire au Préflight |

## Rollback

Rollback applicatif vers `sha-75646d8f` (`1.9.0`), sous responsabilité DevSecOps Lead et
coordination Release Manager. Déclencheurs : échec Flyway/smoke, 5xx anormaux, régression RLS ou
RBAC/ReBAC détectée, incident sur la garde cross-tenant d'archivage. V23/V24 étant additives, les
données créées (profils Gestionnaire enrichis, fiches Locataire) restent intactes mais
temporairement inaccessibles sous `1.9.0` — aucune perte, aucune incompatibilité de schéma.
Restauration de base réservée au seul cas d'intégrité compromise ou de migration incomplète,
depuis le backup vérifié du Préflight.

## Réserves et conditions

| ID | Statut | Traitement |
|---|---|---|
| RSV-EP15-01 — statut Gestionnaire global (risque cross-bailleur) | **Acceptée par le PO** (ADR-16 D1), non bloquante — traçabilité complète par audit | Surveillance continue, sans action Production requise |
| RSV-EP15-02 — backfill V24 sans séparation nom/prénom | Sans objet pour ce Gate | S'applique à la bascule V25 (Sprint C, hors périmètre) |
| RSV-EP15-03 — migration V25 non additive | Sans objet pour ce Gate | S'applique au Sprint C (hors périmètre) |
| RSV-EP15-04 — asymétrie `BienService.archiver()` | Hors périmètre EP-15, dette technique connue | Consignée, non bloquante, à traiter si le PO priorise |
| `RSV-STG-01` (héritée) | Maintenue, sans rapport avec ce périmètre | Inchangée |

Conditions du **Préflight distinct**, avant toute autorisation de déploiement :

1. vérifier Production `1.9.0`, capacité, Flyway 22/22 et observabilité ;
2. vérifier backup base + globals, catalogue, SHA-256 et permissions ;
3. confirmer les digests candidat (`sha256:37de87e8…`/`sha256:7ade9816…`) et la disponibilité du rollback `sha-75646d8f` ;
4. promouvoir le changelog en `[1.10.0]` daté et figer la date de go-live ;
5. préparer un smoke ≥62 et une vérification manuelle live dédiée `/api/gestionnaires`/`/api/locataires` (le script de smoke ne couvre pas ces endpoints, même garantie qu'en Staging) ;
6. confirmer le rollback ciblé API/Nginx (`api`+`nginx` seuls recréés, `postgres`/`keycloak`/monitoring inchangés).

## Avis et décision

| Rôle | Avis |
|---|---|
| Governance Officer | **GO** — checklist et `STG-ISOL-01` PASS, historique de décisions et risques préservé |
| Enterprise Architect | **GO** — V23/V24 additives, RLS Locataire standard, fonctions `SECURITY DEFINER` à surface minimale cohérentes avec ADR-16 |
| DevSecOps Lead | **GO** — CI/images/sécurité/Staging tous PASS ; Préflight obligatoire (pas de secret à injecter, contrairement à `1.9.0`) |
| Release Manager | **GO** — candidat figé `sha-c9200a51` ; aucun déploiement inclus par ce document |
| Product Owner | Plan d'exécution EP-15 respecté (Sprint A+B combinés) ; Sprint C reste hors périmètre jusqu'à un cycle de release Production complet sans anomalie |
| Chief Delivery Officer | **GO — `PRODUCTION_READY`** |

**Décision finale : GO.** `PRODUCTION_READY` est atteint le 2026-07-15.

Cette décision autorise uniquement le **Préflight Production**. Elle n'autorise aucune mutation
ni aucun déploiement. Une instruction explicite distincte est requise après un Préflight PASS.
`PRODUCTION_DEPLOYED` reste non atteint.
