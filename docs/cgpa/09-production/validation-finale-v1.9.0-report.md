# Rapport Validation Finale — Release `1.9.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-06 |
| Fenêtre | 17:52–17:57 UTC |
| Release | `1.9.0` — `sha-75646d8f` |
| Smoke | **62 PASS / 0 FAIL au premier passage** |
| Verdict | **PASS — `PRODUCTION_DEPLOYED` atteint** |

## Smoke Production

Le compte `bailleur-test@test.local` a été activé temporairement puis redésactivé. Le script a
activé puis révoqué automatiquement `directAccessGrants` sur `loyertracker-spa`.

Résultat : **62 PASS / 0 FAIL au premier passage**. Le périmètre couvre Flyway 22/22, le pool
API restreint, les JWT réels, les parcours bailleur/gestionnaire, l'isolation tenant, le RGPD,
les ports internes et la surface publique de vérification des quittances sans oracle.

## Nettoyage et état final

Le run `1783360349` a été nettoyé dans une transaction unique : 9 honoraires, 9 paiements,
6 alertes, 3 audits, 1 affectation, 1 invitation, 1 bail, 1 bien, 2 patrimoines,
1 gestionnaire et 1 second bailleur supprimés. Les comptes Keycloak du run ont été supprimés ;
`bailleur-test` est désactivé et `directAccessGrantsEnabled=false` est confirmé.

| Contrôle | Résultat |
|---|---|
| Résidus du RUN_ID en base | 0 bailleur, 0 gestionnaire |
| Baseline métier | 2 bailleurs, 1 gestionnaire, 1 patrimoine, 3 biens, 3 baux, 75 paiements |
| Garanties / mouvements | 3 / 3, invariant 3/3 |
| Quittances | 0 — attendu au go-live |
| Services | 8/8 actifs, 4/4 healthy, restart=0 |
| Prometheus / Alertmanager | 5/5 `up` / 0 alerte |
| Erreurs API | 2 violations d'unicité attendues du test d'inscription ; 0 inattendue |

**Validation finale PASS — `PRODUCTION_DEPLOYED` atteint le 2026-07-06 vers 17:57 UTC.**
