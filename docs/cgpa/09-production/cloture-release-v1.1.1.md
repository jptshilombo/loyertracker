# Dossier de clôture — Release `1.1.1`

| Champ | Valeur |
|---|---|
| Date de préparation | 2026-06-24 |
| Statut | **Hypercare en cours — T0 PASS sous surveillance** |
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
| Date/heure UTC | 2026-06-24 16:11:35 | Cible : 2026-06-25 04:11:35 | Cible : 2026-06-25 16:11:35 |
| API/Web healthy | PASS — 4 services healthy | En attente | En attente |
| Restart counts | PASS — 0 | En attente | En attente |
| Tag `sha-0adc4941` | PASS — API/Web et digests conformes | En attente | En attente |
| Flyway 14/14 | PASS — 14, rang 14, 0 échec | En attente | En attente |
| Prometheus 5/5 | PASS — cinq cibles `up` | En attente | En attente |
| Alertes actives | PASS — aucune | En attente | En attente |
| HTTP 5xx / logs critiques | PASS sous surveillance — aucun signal Prometheus ; erreurs de validation expliquées | En attente | En attente |
| CPU / mémoire / disque | PASS — charge faible, 1 925 Mio disponibles, disque 15 % utilisé | En attente | En attente |
| Pool JDBC | PASS — pending 0 | En attente | En attente |
| Heartbeat backup | PASS — âge 7 640 s | En attente | En attente |
| Verdict | **PASS sous surveillance** | En attente | En attente |
