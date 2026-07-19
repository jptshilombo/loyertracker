# Rapport Déploiement Technique — Release `1.11.0` (régularisé a posteriori)

| Champ | Valeur |
|---|---|
| Date du déploiement (reconstituée) | 2026-07-16, ~17:53 UTC |
| Date de régularisation documentaire | 2026-07-19 |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Candidat | `sha-cba13f52` |
| Tag précédent | `sha-c9200a51` (`1.10.0`) |
| Autorisation PO | Gate Production GO (`gate-production-ep13-fin-de-bail-decision.md`) + Préflight PASS (`preflight-backup-v1.11.0-report.md`) — instruction explicite de déploiement non retrouvée en trace écrite |
| Verdict | **PASS technique (constaté a posteriori)** |

## Contexte de la régularisation

Le Préflight du 2026-07-16 concluait explicitement que `PRODUCTION_DEPLOYED` n'était pas atteint
et qu'une instruction distincte serait nécessaire pour déployer `api`+`nginx`. Aucun rapport de
déploiement technique ni de validation finale n'a ensuite été rédigé, et le Sprint C EP-15 a
démarré le lendemain sans que ce chaînon soit refermé. La reprise du 2026-07-19 a détecté l'écart
en confrontant la documentation à l'état réel de l'hôte (contrôles lecture seule) : la Production
tournait déjà sur `sha-cba13f52` avec Flyway 25/25, prouvant qu'un déploiement technique avait
bien eu lieu, seulement sans trace écrite.

**Preuves reconstituant la fenêtre du déploiement** :
- Image `sha-cba13f52` (API + Web) construite le 2026-07-16T16:13:04Z (`docker inspect` sur l'hôte).
- `.env.bak-pre-1.11.0` horodaté 2026-07-16 17:53 (sauvegarde de l'`.env` juste avant bascule du tag,
  même patron que tous les Préflights précédents).
- Commit `cba13f5` (`main`) daté 2026-07-16 17:06:46 +0100.

Aucune preuve d'un déploiement ultérieur ou distinct n'a été trouvée : ces éléments convergent
vers une fenêtre unique, ~17:50–17:55 UTC le 2026-07-16, immédiatement après le Préflight.

## Constats techniques (vérifiés en direct le 2026-07-19)

| Contrôle | Résultat |
|---|---|
| `LOYERTRACKER_TAG` sur l'hôte | `sha-cba13f52` |
| Digests images actives | API `sha256:ea040492bb5ad6b6a72b84665e22cd47a66d79c293b874fca481d5a276afe1c8` ; Web `sha256:e70ebc7ba7d71406edaec6f890c2f57f06ae9d7c855680e0fba01914b4251968` — conformes au candidat `sha-cba13f52` |
| Flyway | **25/25** appliquées (V25 `bail.date_cloture_effective` + `generer_alertes()` filtrée `ACTIF` confirmée) |
| Services | 8/8 actifs, 4/4 healthy, `RestartCount=0` sur `api`/`nginx`/`postgres`/`keycloak` |
| `/healthz` | 200 |
| Production publique | `https://loyertracker.loyerpro.org` → 200 |
| Prometheus / Alertmanager | 5/5 cibles `up` ; 0 alerte active |
| 5xx (30 min) | 0 |
| Données métier baseline | 3 bailleurs, 2 patrimoines, 8 biens, 8 baux, 8 garanties, 1 gestionnaire, 0 locataire, 6 quittances — identique à la clôture `1.10.0` |
| Rollback | `sha-c9200a51` — migration V25 additive, rollback applicatif seul viable (cf. `gate-production-ep13-fin-de-bail-decision.md`) |

**Déploiement technique constaté conforme.** Le canal exact par lequel l'instruction de
déploiement a été donnée n'a pas pu être retrouvé dans l'historique écrit — écart de traçabilité
documenté et fermé ci-dessous (§ Réserve de gouvernance), sans conséquence sur l'état technique
réel, entièrement vérifié conforme.

## Réserve de gouvernance — écart de traçabilité du déploiement `1.11.0`

**Constat** : le déploiement de `1.11.0` sur `loyertracker-prod-server` a bien eu lieu (preuves
ci-dessus), mais aucun rapport de déploiement technique ni de validation finale n'a été rédigé au
moment des faits ; `project-state.md` et `prod-state.md` s'arrêtaient au Préflight PASS. Le Sprint
C EP-15 a démarré le lendemain sur cette base sans que le chaînon Gate Production soit refermé
formellement.

**Régularisation (2026-07-19)** : ce rapport, `validation-finale-v1.11.0-report.md` et la mise à
jour de `prod-state.md`/`project-state.md` referment le chaînon a posteriori, sur la base de
contrôles lecture seule et d'un smoke de validation finale rejoué en conditions réelles (voir
rapport dédié). Aucune donnée de production n'a été affectée par cet écart documentaire — l'écart
est purement gouvernance/traçabilité, pas opérationnel.

**Leçon retenue** : après un Préflight PASS, la bascule du tag `.env` en Production doit être
immédiatement suivie (même session) de la rédaction du rapport de déploiement technique et de la
validation finale, avant tout démarrage d'un nouveau Sprint — même schéma que la leçon R-S04-1
(tout écart de séquence CGPA doit être régularisé avant de s'accumuler).
