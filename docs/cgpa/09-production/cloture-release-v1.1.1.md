# Dossier de clôture — Release `1.1.1`

| Champ | Valeur |
|---|---|
| Date de préparation | 2026-06-24 |
| Statut | **Hypercare en cours — T0 et T+12 PASS sous surveillance ; T+24 attendu 2026-06-25 16:11:35 UTC** |
| Version | `1.1.1` |
| Commit applicatif | `0adc4941f854304a3f7412b04294615b05403707` |
| Tag Production | `sha-0adc4941` |
| Rollback | `sha-05424aa3` |
| Backup | `loyertracker-20260624-140441.dump` |
| Merge documentaire | `083720dc6b7493e9ac5ed684bf39936d46e8b2b7` |

## Références

- Gate : `gate-production-v1.1.1-hotfix-decision.md`.
- Préflight/backup : `preflight-backup-v1.1.1-report.md`.
- Déploiement : `deploiement-technique-v1.1.1-report.md`.
- Validation finale : `validation-finale-v1.1.1-report.md`.
- Release notes : `docs/release-notes-v1.1.1.md`.

## Baseline avant hypercare

Baseline issue de la validation finale, à reconfirmer lors du T0 :

- API/Web sur `sha-0adc4941` ;
- services applicatifs healthy, restart count 0 ;
- Flyway V1→V14 ;
- Actuator UP et assets Web HTTP 200 ;
- cinq cibles Prometheus `up` ;
- aucune alerte ;
- smoke 47 PASS / 0 FAIL et données de test nettoyées ;
- backup vérifié et rollback disponible.

Ces éléments ne constituent pas les mesures d'hypercare. Les observations T0/T+12/T+24 seront
renseignées uniquement après validation du plan Étape 2.

## Registre hypercare 24 heures

| Contrôle | T0 | T+12 h | T+24 h |
|---|---|---|---|
| Date/heure UTC | 2026-06-24 16:11:35 | Cible 2026-06-25 04:11:35 ; fenêtre **étendue par décision CDO** (cf. note ci-dessous) ; exécuté **2026-06-25 06:21:54** | Cible : 2026-06-25 16:11:35 |
| API/Web healthy | PASS — 4 services healthy | PASS — `api`, `nginx`, `postgres`, `keycloak` `(healthy)` ; 8/8 conteneurs `Up` (incl. `alertmanager`/`blackbox`/`prometheus`/`pushgateway`, sans healthcheck dédié) | En attente |
| Restart counts | PASS — 0 | PASS — 0 sur les 8 conteneurs | En attente |
| Tag `sha-0adc4941` | PASS — API/Web et digests conformes | PASS — `LOYERTRACKER_TAG=sha-0adc4941` inchangé ; digests identiques au rapport de déploiement : API `sha256:602c9418ac9c2329cd2989045eec1f6194cac72830e3cb27794a5ee9fc429016`, Web `sha256:21c18e7d3f3d4656d60c8242d7550d05bbc8252dc96a4a81b5a65e3d4215c4a3` (zéro dérive) | En attente |
| Flyway 14/14 | PASS — 14, rang 14, 0 échec | PASS — 14, rang max 14, 0 échec | En attente |
| Prometheus 5/5 | PASS — cinq cibles `up` | PASS — cinq cibles `up` (`loyertracker-api`, `blackbox-keycloak`, `blackbox-postgres`, `prometheus`, `pushgateway`) | En attente |
| Alertes actives | PASS — aucune | PASS — aucune (Alertmanager `GET /api/v2/alerts` → `[]`) | En attente |
| HTTP 5xx / logs critiques | PASS sous surveillance — aucun signal Prometheus ; erreurs de validation expliquées | PASS — 0 ligne 5xx (`api`/`nginx`), ratio 5xx/5 min = 0, p99 ≈ 20,7 ms, 401 cumulés = 5 en 16 h (taux ≪ seuil 1/s) ; PostgreSQL 2 occurrences `FATAL: role "root" does not exist` **expliquées** (effet de bord connu de `pg_isready` sans `-U`, déclenché par ce contrôle lui-même à T0 puis à T+12, aucune autre source) ; Keycloak 0 erreur | En attente |
| CPU / mémoire / disque | PASS — charge faible, 1 925 Mio disponibles, disque 15 % utilisé | PASS — charge 0,11/0,05/0,01 (2 vCPU), mémoire disponible 1,8 Gio/3,7 Gio, swap 0, disque 33 Go libres/38 Go (15 % utilisé), inodes 3 % utilisés, aucun montage en lecture seule | En attente |
| Pool JDBC | PASS — pending 0 | PASS — actifs 0, idle 10, max 10, pending 0 | En attente |
| Heartbeat backup | PASS — âge 7 640 s | PASS — heartbeat backup âge ≈ 14 875 s (~4 h 08) ; derniers batchs `alertes` ≈ 4 977 s (~1 h 23) et `loyers` ≈ 6 777 s (~1 h 53), tous sous le seuil 26 h | En attente |
| Identité dépôt hôte | — | Note — `git rev-parse HEAD` sur l'hôte renvoie encore `05424aa3` (commit source de `1.1.0`) ; conforme au constat déjà documenté (`docs/cgpa/loyertracker-prod-server` mémoire opérationnelle) : le checkout du dépôt sur l'hôte sert uniquement aux scripts d'exploitation et n'est jamais la source du déploiement (images GHCR épinglées par tag/digest immuable, jamais de build local). Aucun changement inexpliqué : 3 fichiers `.env.pre-*`/`.env_bkp` non suivis, déjà connus comme sauvegardes de la persistance du tag. N'affecte pas le verdict. | En attente |
| Verdict | **PASS sous surveillance** | **PASS sous surveillance** — fenêtre T+12 étendue par décision CDO ; aucune anomalie bloquante ; réserve `RSV-STG-01` et dette CORS inchangées | En attente |

### Note — Décision CDO sur la fenêtre T+12

La fenêtre cible T+12 (2026-06-25 03:41:35–04:41:35 UTC) a été dépassée de plus de 30 minutes
avant que l'accès Production ne soit autorisé (vérification documentaire CGPA v5.4.1 en cours
sur la même session). Conformément à la règle du plan (§4.5), aucun résultat n'a été inventé
rétroactivement à l'heure cible : le Chief Delivery Officer a tranché, à la demande du PO,
**l'extension de la fenêtre T+12** (motif : retard documentaire sans rapport avec la Production,
aucun signal d'alerte reçu entre-temps) plutôt qu'un redémarrage complet de l'hypercare. Le
checkpoint a été exécuté à l'heure réelle **2026-06-25 06:21:54 UTC** et consigné comme tel.
L'échéance **T+24 reste ancrée au T0 d'origine** (2026-06-25 16:11:35 UTC).

### Note — Lecture Keycloak à T+12

La vérification directe `curl` dans le conteneur `keycloak` a échoué (`curl` absent de l'image
`quay.io/keycloak/keycloak:24.0`) — limite d'outillage du contrôle, sans rapport avec l'état du
service. La disponibilité de Keycloak est confirmée par deux canaux indépendants déjà en place :
l'état `(healthy)` du healthcheck Docker et la sonde `blackbox-keycloak` de Prometheus
(`probe_success=1`, `probe_http_status_code=200`), qui alimente l'alerte `KeycloakProbeDown`.
