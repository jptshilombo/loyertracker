# Rapport Déploiement Technique — Release `1.9.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-06 |
| Fenêtre | 17:50:11–17:50:53 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Candidat | `sha-75646d8f` |
| Tag précédent | `sha-2c5f43c7` (`1.8.0`) |
| Autorisation PO | Explicite, après Préflight PASS et merge PR #205 |
| Verdict | **PASS technique** |

## Exécution ciblée

Le dépôt Production a été avancé par fast-forward de `97f7caf1` à `3feb1d5` (merge PR #205).
Les digests correspondent au Gate Production :

- API : `sha256:3c2279102fd4bf902ea82946d37d13faab8d8c98124c44b7c651666d7aa71aff` ;
- Web : `sha256:f0146fa6ca87733e6aebc1f127d19db4c9bd2b233b18d198a3b02e819e319a04`.

Le tag `.env` a été basculé vers `sha-75646d8f`, avec `.env.bak-pre-1.9.0` disponible. Seuls
`api` et `nginx` ont été tirés et recréés. PostgreSQL, Keycloak et les quatre services de
monitoring sont restés actifs. Aucune commande Docker globale n'a été exécutée.

## Migration et contrôles

Flyway a validé 22 migrations puis appliqué `22 - ep14 quittance certifiee` en 137 ms. Les trois
tables V22 sont présentes ; l'invariant ledger reste cohérent pour 3/3 garanties.

| Contrôle | Résultat |
|---|---|
| Services | 8/8 actifs, 4/4 healthy, restart=0 |
| Images actives | tag et digests exacts du Gate |
| Flyway | V22 appliquée, 22/22 |
| `/healthz` / Actuator | `ok` / `UP` |
| Production publique | HTTP 200 |
| Prometheus / Alertmanager | 5/5 `up` / 0 alerte |
| Rollback | `sha-2c5f43c7`, V22 additive, backup Préflight disponible |

**Déploiement technique PASS.** La validation finale a été exécutée dans la même session.
