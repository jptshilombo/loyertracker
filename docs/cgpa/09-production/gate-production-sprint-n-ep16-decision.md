# Gate Production — EP-16 Sprint N (Fondation notifications)

| Champ | Valeur |
|---|---|
| Date | 2026-07-19 |
| Version | `1.13.0` MINOR (proposée — à confirmer et dater au Préflight) |
| Candidat applicatif | merge PR #236 `e4744d9`, tag immuable `sha-e4744d92` |
| Digests Staging | API `sha256:9e9a331d3a7ee8a65e17235ead3f60c4b916d46086d9d3dd2d0c263ddabfe815` ; Web `sha256:c797934a8d5e629a6e532c50790dad78495a5e1aa5e7d42273e7fc6ccd00d41b` |
| Source | `ai-test-server`, `STAGING_DEPLOYED`, `STG-ISOL-01` PASS |
| Production actuelle / rollback | `1.12.0`, `sha-359f4d63` |
| Décision | **GO sous réserve — `PRODUCTION_READY`** |

## Périmètre

EP-16 Sprint N, US-119/120/121 : fondation des notifications multicanales, migration additive
V27, modèle de préférences/événements/Outbox/livraisons/templates, alimentation transactionnelle
des événements, réclamation concurrente de l'Outbox et abstraction `NotificationProvider`.

Le seul fournisseur livré est `NoopNotificationProvider`. Aucun SDK, credential, template ou
appel réseau Twilio n'est présent. Les notifications externes, WhatsApp et SMS sont désactivés
par défaut ; le dry-run est activé. Le Gate ne couvre ni Sprint N+1, ni Sprint N+2, ni
l'activation de canaux externes.

Le commit `main` post-fusion de la PR documentaire #237 est `cc2fd06`. Son delta depuis
`e4744d9` contient exclusivement quatre fichiers documentaires ; l'artefact applicatif évalué et
déployé en Staging reste donc `sha-e4744d92`.

## Checklist CGPA v5.4.1

| Critère | Statut | Preuve |
|---|---|---|
| Identification complète | PASS | Sprint N EP-16, `1.13.0` proposée, tag/digests et environnements identifiés |
| Candidat exact en Staging | PASS | `sha-e4744d92`, Gate Staging GO |
| `STG-ISOL-01` | PASS | 9 conteneurs projet+NPM intacts avant/après, restart=0, seuls API/Nginx recréés, aucune commande Docker globale |
| Migration | PASS | V27 additive, Flyway 27/27 ; cinq nouvelles tables, RLS ENABLE+FORCE sur les quatre tables tenant-scopées |
| Smoke Staging | PASS | 63 PASS / 0 FAIL au premier passage |
| Validation fonctionnelle | PASS | 24 événements persistés ; 0 préférence, 0 Outbox, 0 Delivery et 0 référence Twilio : aucun envoi sans consentement |
| CI / tests | PASS | CI `main` `29695395488` SUCCESS : Backend, Frontend, Sécurité, Packaging Docker ; CodeQL `29695395451` SUCCESS |
| SonarQube | PASS | analyse Backend bloquante verte après correction de `java:S107` et `java:S6204` |
| Sécurité | PASS | Gitleaks, SCA, Trivy API/Web et CodeQL verts ; RLS cross-tenant vérifiée ; aucun secret ajouté ou lu |
| Release notes / changelog | PASS | section `[Non publié]` ajoutée dans `CHANGELOG.md` ; ce document fixe le périmètre et le hors-périmètre de la RC |
| Observabilité | PASS | Staging Prometheus 5/5, 0 erreur API, 0 HTTP 5xx ; aucune métrique fournisseur requise avant Sprint N+2 |
| PO / Release Manager | PASS | instruction PO de passage au GO Production et approbation d'exécution reçues le 2026-07-19 ; audit Release Manager présent |
| État release précédente | **PASS sous réserve bloquante** | `1.12.0` est `PRODUCTION_DEPLOYED`, T0 PASS ; T+12/T+24 et clôture CDO restent à exécuter |

## Rollback

V27 est additive. Le rollback applicatif ciblé vers `sha-359f4d63` (`1.12.0`) reste viable après
migration : l'ancienne application ignore les cinq nouvelles tables. La procédure cible
uniquement API et Nginx ; PostgreSQL, Keycloak et monitoring ne doivent pas être recréés.

Le Préflight devra néanmoins produire et vérifier un backup base + globals avant migration,
confirmer le catalogue et les SHA-256, puis conserver le point de restauration. La restauration
complète n'est requise que si les données V27 doivent elles-mêmes être supprimées ; aucun
`DROP` de ces tables n'est prévu dans un rollback applicatif normal.

## Réserves et conditions

| ID | Statut | Traitement |
|---|---|---|
| RSV-PROD-EP16-N-01 — release `1.12.0` encore en hypercare | **Ouverte, bloquante avant Préflight** | Exécuter T+12 et T+24, puis obtenir la clôture CDO de `1.12.0` |
| RSV-PROD-EP16-N-02 — version `1.13.0` proposée | Ouverte jusqu'au Préflight | Confirmer SemVer/date et promouvoir `[Non publié]` en `[1.13.0]` |
| RSV-STG-01 — accès public Staging protégé par Access List NPM | Héritée, non bloquante | Sans rapport avec le candidat ; preuve Gate obtenue par accès direct autorisé |

Conditions bloquantes du Préflight distinct :

1. clôturer formellement l'hypercare `1.12.0` après checkpoints T+12 et T+24 PASS ;
2. vérifier en lecture seule la Production `1.12.0`, sa capacité, Flyway 26/26 et son
   observabilité ;
3. produire et vérifier backup base + globals avant V27 ;
4. confirmer le tag `sha-e4744d92`, les deux digests Staging et la disponibilité du rollback
   `sha-359f4d63` ;
5. promouvoir le changelog en `[1.13.0]` daté et figer la fenêtre ;
6. conserver tous les flags externes à leurs valeurs sûres et confirmer l'absence de toute
   configuration ou dépendance Twilio ;
7. préparer le smoke canonique ≥63 et un contrôle spécifique : événements possibles, mais 0
   Outbox/Delivery sans préférence ;
8. cibler exclusivement API/Nginx pour déploiement ou rollback.

## Avis et décision

| Rôle | Avis |
|---|---|
| Governance Officer | **GO sous réserve** — checklist complète, historique préservé, `STG-ISOL-01` PASS ; séquencement de `1.12.0` rendu bloquant avant Préflight |
| Enterprise Architect | **GO** — V27 additive, isolation RLS conforme, rollback applicatif viable, aucun fournisseur externe réel |
| DevSecOps Lead | **GO sous réserve** — CI, SonarQube, sécurité, images, Flyway, smoke et observabilité PASS ; backup Préflight obligatoire |
| Release Manager | **GO sous réserve** — candidat figé ; Préflight interdit avant clôture `1.12.0` |
| Product Owner | **GO** — instruction explicite de passage au GO Production reçue le 2026-07-19 |
| Chief Delivery Officer | **GO sous réserve — `PRODUCTION_READY`** |

**Décision finale : GO sous réserve.** `PRODUCTION_READY` est atteint le 2026-07-19.

Cette décision autorise uniquement un **Préflight Production après levée de
RSV-PROD-EP16-N-01**. Elle n'autorise aucune mutation ni aucun déploiement Production.
`PRODUCTION_DEPLOYED` reste non atteint. L'activation des canaux externes reste interdite jusqu'à
la clôture en GO du Sprint N+2, conformément à K8.
