# Gate Production — Release `1.9.0` (EP-14, Sprints 11–12 + Angular 22)

| Champ | Valeur |
|---|---|
| Date | 2026-07-06 |
| Version | `1.9.0` MINOR |
| Candidat | `75646d8ff8ea789b3c67f7977a8852c50cf68119` / `sha-75646d8f` |
| Digest API | `sha256:3c2279102fd4bf902ea82946d37d13faab8d8c98124c44b7c651666d7aa71aff` |
| Digest Web | `sha256:f0146fa6ca87733e6aebc1f127d19db4c9bd2b233b18d198a3b02e819e319a04` |
| Source | `ai-test-server`, `STAGING_DEPLOYED` |
| Production / rollback | `1.8.0`, `sha-2c5f43c7` |
| Décision | **GO — `PRODUCTION_READY`** |

## Périmètre

Release unique ADR-15 K5 : Sprint 11 EP-14a US-99/100/101, Sprint 12 EP-14b
US-102/103/104 et Angular 22. Le candidat est exactement celui validé en Staging. PR #203
(`7f52b10`) est documentaire et ne modifie pas les images.

## Checklist CGPA v5.4.1

| Critère | Statut | Preuve |
|---|---|---|
| Identification complète | PASS | EP-14, `1.9.0`, tag/digests, Staging vers Production |
| Candidat exact en Staging | PASS | `sha-75646d8f`, Gate Staging GO |
| `STG-ISOL-01` | PASS | NPM et 9 conteneurs intacts ; réseaux/volumes/ports inchangés ; restart=0 |
| Smoke / Flyway | PASS | **62/0 au premier passage** ; 22/22 |
| Fonctionnel | PASS | `QT-2026-000001` VALIDE ; PDF conforme à `pdf_hash` |
| Sécurité | PASS | revue dédiée ; absence d'oracle, K2, HMAC, re-hash, 429, CSP/HSTS |
| CI / tests | PASS | CI/CodeQL SUCCESS ; PR #202 7/7 ; backend 162/162 ; frontend 94/94 |
| SonarQube | PASS | Quality Gate PASSED ; couverture nouvelle 89,8 % ; 0 violation nouvelle |
| Migration / rollback | PASS | V22 additive ; `1.8.0` ignore les nouveaux objets |
| Observabilité | PASS | trois métriques dédiées confirmées en Staging |
| PO / Release Manager | PASS | instruction PO explicite ; candidat, digests et rollback validés |
| Release notes / changelog | PASS | notes finalisées ; EP-14 sous `[Non publié]`, promotion au Préflight |
| Secrets | PASS sous condition | secret HMAC Production distinct à injecter au Préflight |

Quatre alertes Dependabot (3 modérées, 1 basse) concernent des dépendances npm transitives de
**développement** (`webpack-dev-server`, `uuid`, `@babel/core`). Le stage final Nginx copie
seulement le build statique Angular : aucune exposition runtime. SCA/Trivy/CodeQL sont verts.
Qualification : **non bloquantes**, maintenance frontend.

## Rollback

Rollback applicatif vers `sha-2c5f43c7`, sous responsabilité DevSecOps Lead et coordination
Release Manager. Déclencheurs : migration/smoke en échec, 5xx anormaux, hash incohérent ou
régression sécurité. Les données V22 restent intactes mais temporairement inaccessibles sous
`1.8.0`. Restauration de base seulement en cas d'intégrité compromise ou migration incomplète,
depuis le backup vérifié du Préflight.

## Réserves et conditions

| ID | Statut | Traitement |
|---|---|---|
| RSV-PROD-EP14-01 — route QR absente | **LEVÉE** | Sprint 12 livré, parcours réel vérifié |
| RSV-PROD-EP14-02 — Angular 22 empilé | **LEVÉE** | option A exécutée : inclus dans `1.9.0` |
| OBS-PROD-190-01 — alertes npm dev | **Non bloquante** | aucune exposition runtime |

Conditions du **Préflight distinct**, avant toute autorisation de déploiement :

1. vérifier Production, capacité, Flyway 21/21 et observabilité ;
2. vérifier backup base + globals, catalogue, SHA-256 et permissions ;
3. confirmer digests candidat et disponibilité du rollback ;
4. injecter un `QUITTANCE_HMAC_SECRET` Production distinct, persistant et non exposé ;
5. confirmer `QUITTANCE_TOKEN_KID` et l'URL Production ;
6. promouvoir le changelog en `[1.9.0]` daté et figer la date de go-live ;
7. préparer smoke 62+ et contrôle QR/PDF sans afficher ni persister le token ;
8. confirmer le rollback ciblé API/Nginx.

## Avis et décision

| Rôle | Avis |
|---|---|
| Governance Officer | **GO** — checklist et STG-ISOL-01 PASS |
| Enterprise Architect | **GO** — EP-14 cohérent, V22 additive |
| DevSecOps Lead | **GO** — CI/images/sécurité/Staging verts ; Préflight obligatoire |
| Release Manager | **GO** — candidat figé ; aucun déploiement inclus |
| Chief Delivery Officer | **GO — `PRODUCTION_READY`** |

**Décision finale : GO.** `PRODUCTION_READY` est atteint le 2026-07-06.

Cette décision autorise uniquement le **Préflight Production**. Elle n'autorise aucune mutation
ni aucun déploiement. Une instruction explicite distincte est requise après un Préflight PASS.
`PRODUCTION_DEPLOYED` reste non atteint.
