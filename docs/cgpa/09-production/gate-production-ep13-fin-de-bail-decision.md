# Gate Production — Sprint unique EP-13 (Fin de bail)

| Champ | Valeur |
|---|---|
| Date | 2026-07-16 |
| Version | `1.11.0` MINOR (proposée — à confirmer/dater au Préflight) |
| Candidat | `cba13f5253fde403d1c20b3ecf68ec6aade9a707` / tag `sha-cba13f52` |
| Digest API | `sha256:72411f5ca7f116520871eb5d1ef1439b2d338bbcd5922359eb7dbd9398ae37d0` |
| Digest Web | `sha256:63347a957bc235b870bacfdf94777163b19ac7c39a2362b93f43b1f6a40044c8` |
| Source | `ai-test-server`, `STAGING_DEPLOYED` (`gate-staging-ep13-fin-de-bail-decision.md`) |
| Production / rollback | `1.10.0`, `sha-c9200a51` |
| Décision | **GO — `PRODUCTION_READY`** |

## Périmètre

Sprint unique EP-13 (US-115→118, ADR-17 Acceptée, Plan d'Exécution approuvé) : clôture manuelle
d'un `Bail` (`ACTIF → CLOS`, K1/K2), avertissements non bloquants si garantie non restituée
et/ou paiements en cours (K3/K4), réouverture (K5), purge de l'échéancier futur `A_VENIR` (K6),
non-régression du batch d'alertes (`LOYER_EN_RETARD` désormais restreinte aux baux `ACTIF`,
US-118).

**Hors périmètre** (déjà tranché par l'ADR-17, non rejoué ici) : tout `ClotureBailService`
orchestrant Garantie+Paiements+Bail en une transaction unique ; toute modification des modules
Garantie ou Paiements eux-mêmes ; toute UI Angular (backend-only, comme le précédent Locataire).
Aucune dépendance technique avec le Sprint C d'EP-15 (bascule `Bail.locataireId`, toujours NO-GO
« pas encore ») — coordination de séquencement Production à arbitrer séparément si les deux lots
sont un jour conduits en parallèle (RSV-EP13-04, non bloquant pour ce Gate).

## Checklist CGPA v5.4.1

| Critère | Statut | Preuve |
|---|---|---|
| Identification complète | PASS | EP-13 Sprint unique, `1.11.0` proposée, tag/digests, Staging vers Production |
| Candidat exact en Staging | PASS | `sha-cba13f52`, Gate Staging GO (`gate-staging-ep13-fin-de-bail-decision.md`) |
| `STG-ISOL-01` | PASS | 9 conteneurs `loyertracker-staging-*` + NPM mutualisé intacts avant/après, restart=0, aucune commande Docker globale |
| Smoke / Flyway | PASS | Smoke 62/0 ; Flyway V25 appliquée, 25/25 |
| Vérification fonctionnelle dédiée | PASS | 22/0 sur `.../cloture`/`.../reouverture` (avertissements K3/K4 sans blocage, `dateFin` inchangée, purge `A_VENIR` sans toucher l'historique, non-régression `LOYER_EN_RETARD`, collisions 409) — cf. `gate-staging-ep13-fin-de-bail-decision.md` |
| Sécurité | PASS — sans nouvelle surface | RBAC/ReBAC inchangés (même garde `@authz.peutAccederBien` que la création de bail) ; aucune nouvelle fonction `SECURITY DEFINER` (la clôture ne traverse aucune frontière cross-tenant, contrairement au statut Gestionnaire EP-15) ; pas de revue sécurité dédiée jugée nécessaire pour ce périmètre (confirmé par ADR-17 §Impacts sécurité) |
| CI / tests | PASS | CI GitHub 7/7 SUCCESS sur `cba13f5` (Backend, Frontend, CodeQL java-kotlin+js-ts, Sécurité gitleaks+SCA+Trivy, Packaging Docker) ; `mvn verify` 185/185 |
| SonarQube | PASS | Quality Gate bloquant vert — un correctif (`java:S5778`, lambda de test) a été nécessaire avant merge, déjà intégré au candidat |
| Migration / rollback | PASS | V25 additive uniquement (`bail.date_cloture_effective` nullable, `CREATE OR REPLACE FUNCTION generer_alertes`) — `1.10.0` ignore la nouvelle colonne, rollback applicatif seul viable |
| Secrets Production | PASS — sans objet | Aucune nouvelle variable d'environnement/secret introduite — confirmé par `git diff --stat c9200a5 cba13f5` sur les fichiers Compose/`.env.example` (aucun changement) |
| Observabilité | PASS | Aucun nouveau dispositif requis pour ce périmètre ; dispositif Production existant inchangé |
| PO / Release Manager | PASS | Plan d'Exécution EP-13 respecté (Sprint unique, aucune extension) ; candidat, digests et rollback identifiés |
| Release notes / changelog | PASS sous condition | `CHANGELOG.md` `[Non publié]` couvre déjà le Sprint EP-13 ; promotion en `[1.11.0]` daté à faire au Préflight |

## Rollback

Rollback applicatif vers `sha-c9200a51` (`1.10.0`), sous responsabilité DevSecOps Lead et
coordination Release Manager. Déclencheurs : échec Flyway/smoke, 5xx anormaux, régression RBAC/
ReBAC sur `.../baux/{bailId}/...`, purge erronée de l'échéancier, régression du batch d'alertes.
V25 étant additive, les baux clôturés/rouverts sous ce Sprint restent intacts mais la colonne
`date_cloture_effective` devient inerte sous `1.10.0` (aucune perte, aucune incompatibilité de
schéma) — un bail redevenu inaccessible en clôture applicative resterait néanmoins listé/consultable
normalement. Restauration de base réservée au seul cas d'intégrité compromise, depuis le backup
vérifié du Préflight.

## Réserves et conditions

| ID | Statut | Traitement |
|---|---|---|
| RSV-EP13-01 — clôture sans garantie restituée ni impayés soldés (K3/K4 non bloquants) | **Acceptée par le PO** (kickoff 2026-07-16), non bloquante — avertissement + réouverture possible | Surveillance continue, aucune action Production requise |
| RSV-EP13-02 — purge d'échéances `A_VENIR` (K6) | **Acceptée par le PO**, limitée aux échéances strictement futures non exigibles | Aucune action Production requise |
| RSV-EP13-03 — collision de réouverture | Mitigée par construction (`uq_bail_actif` déjà en base) | Aucune action requise |
| RSV-EP13-04 — coordination avec le Sprint C d'EP-15 | Sans objet pour ce Gate (Sprint C toujours NO-GO « pas encore ») | À réexaminer si le Sprint C démarre avant une future release |
| `RSV-STG-01` (héritée) | Maintenue, sans rapport avec ce périmètre | Inchangée |
| Incident mineur — exposition accidentelle de secrets Staging (`set -x`) pendant le Gate Staging | Signalé au PO, rotation recommandée sur `ai-test-server` | **Sans rapport avec la Production** — aucun secret Production concerné, non bloquant pour ce Gate |

Conditions du **Préflight distinct**, avant toute autorisation de déploiement :

1. vérifier Production `1.10.0`, capacité, Flyway 24/24 et observabilité ;
2. vérifier backup base + globals, catalogue, SHA-256 et permissions ;
3. confirmer les digests candidat (`sha256:72411f5c…`/`sha256:63347a95…`) et la disponibilité du rollback `sha-c9200a51` ;
4. promouvoir le changelog en `[1.11.0]` daté et figer la date de go-live ;
5. préparer un smoke ≥62 et une vérification manuelle live dédiée `.../cloture`/`.../reouverture` (le script de smoke ne couvre pas ces endpoints, même garantie qu'en Staging) ;
6. confirmer le rollback ciblé API/Nginx (`api`+`nginx` seuls recréés, `postgres`/`keycloak`/monitoring inchangés).

## Avis et décision

| Rôle | Avis |
|---|---|
| Governance Officer | **GO** — checklist et `STG-ISOL-01` PASS, historique de décisions et risques préservé, kickoff K1→K6 clos |
| Enterprise Architect | **GO** — V25 additive, garde métier sur l'entité (patron `Locataire`), aucun recouplage Garantie/Paiements, aucune nouvelle fonction `SECURITY DEFINER` |
| DevSecOps Lead | **GO** — CI/images/Staging tous PASS ; Préflight obligatoire |
| Release Manager | **GO** — candidat figé `sha-cba13f52` ; aucun déploiement inclus par ce document |
| Product Owner | Plan d'Exécution EP-13 respecté (Sprint unique, périmètre inchangé) |
| Chief Delivery Officer | **GO — `PRODUCTION_READY`** |

**Décision finale : GO.** `PRODUCTION_READY` est atteint le 2026-07-16.

Cette décision autorise uniquement le **Préflight Production**. Elle n'autorise aucune mutation
ni aucun déploiement. Une instruction explicite distincte est requise après un Préflight PASS.
`PRODUCTION_DEPLOYED` reste non atteint.
