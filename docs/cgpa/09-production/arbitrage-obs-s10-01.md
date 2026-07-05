# Arbitrage OBS-S10-01 — Tie-break UUID intra-timestamp du ledger de garantie

| Champ | Valeur |
|---|---|
| Date | 2026-07-05 |
| Origine | Observation consignée au Gate Production `1.8.0` (S1) et reportée « à statuer post-clôture » par `cloture-release-v1.8.0.md` §3 et `plan-etape-hypercare-v1.8.0.md` |
| Décision | **ACCEPTÉE EN L'ÉTAT** (PO, sur instruction du 2026-07-05) — avec deux garde-fous (§4) |
| Statut | **CLOSE** |

## 1. Rappel de l'observation

Le correctif RSV-S10-01 (PR #173) a rendu le tri du ledger stable et chronologique :
`ORDER BY date_mouvement, cree_le, id`. Il subsiste un cas limite résiduel : deux mouvements
partageant **exactement le même `cree_le`** retombent sur le tie-break par `id` (UUID
aléatoire, donc ordre arbitraire entre eux).

Or `cree_le` est posé par Postgres via `now()`, qui est le **timestamp de transaction** : tous
les mouvements insérés dans une même transaction partagent le même `cree_le`. C'est le cas des
lignes **backfillées par V20** (reconstitution d'historique en une transaction lors du
déploiement `1.7.0`), et ce serait le cas de toute future insertion multi-mouvements en une
transaction.

## 2. Assiette réelle — vérifiée en Production le 2026-07-05

Requête de contrôle (réutilisable pour toute réévaluation) :

```sql
SELECT garantie_id, date_mouvement, cree_le, count(*)
FROM garantie_movement
GROUP BY 1, 2, 3
HAVING count(*) > 1;
```

Résultat en Production (2026-07-05, post-clôture `1.8.0`) : **0 ligne**. Les 3 garanties
réelles ont chacune **exactement 1 mouvement** (1 `cree_le` distinct chacune) — aucun cas
d'ambiguïté possible, ni aujourd'hui ni rétroactivement (le backfill V20 est un événement passé
et clos ; il ne produira plus de lignes).

## 3. Analyse

- **Impact fonctionnel nul constaté** : 0 cas en Production. Même en présence d'un cas, l'effet
  serait purement cosmétique (ordre d'affichage intra-seconde dans l'historique US-97 et
  l'export) — chaque mouvement porte son `solde_apres`, les soldes et l'invariant du ledger sont
  insensibles à l'ordre.
- **Vecteur de croissance quasi inexistant dans le code actuel** : chaque endpoint métier
  (dépôt, retenue, complément, restitution) insère **un seul** mouvement par transaction — deux
  appels distincts obtiennent des `cree_le` distincts. Seule une future fonctionnalité insérant
  plusieurs mouvements dans une même transaction (traitement par lot, reprise de données)
  recréerait la condition.
- **Coût du correctif disproportionné** : introduire un critère métier secondaire (rang de type)
  ou une colonne de séquence exigerait une migration et un choix de sémantique d'ordre pour un
  problème sans occurrence réelle.

## 4. Décision et garde-fous

**OBS-S10-01 est ACCEPTÉE EN L'ÉTAT et CLOSE.** Aucun correctif planifié. Deux garde-fous
consignés :

1. **Réévaluation sur premier cas réel** : si la requête de contrôle (§2) renvoie une ligne sur
   des données réelles (hors backfill), rouvrir l'observation et arbitrer alors un critère de
   tri secondaire.
2. **Point de vigilance conception** : toute future fonctionnalité insérant **plusieurs
   mouvements de garantie dans une même transaction** (batch, import, reprise) doit prévoir un
   ordre déterministe explicite (séquence applicative ou critère métier) — à vérifier au Gate
   Staging du sprint concerné.

Cet arbitrage clôt le dernier point post-clôture de la release `1.8.0` listé par
`plan-etape-hypercare-v1.8.0.md`. Reste au backlog transverse : les 2 alertes Dependabot sur
`main` (1 modérée, 1 basse), hors périmètre garantie.
