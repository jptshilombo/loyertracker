# ADR-14 — Garantie comme compte à mouvements (ledger) et préparation Fin de bail (EP-12/EP-13)

| Champ | Valeur |
|-------|--------|
| Code de décision | **D-GAR-001** |
| Statut | **Acceptée** — kickoff Sprint 9 le 2026-07-02 (Plan d'Exécution `plan-execution-evolutions-ep10-ep13.md`, Sprints 9-10) |
| Date | 2026-07-01 |
| Phase | 07 — Développement (lot post-`1.5.0`, continu) |
| Documents liés | `analyse-impact-evolutions-ep10-ep13.md` §3-4, `addendum-backlog-ep10-ep12.md` (US-94→98), ADR-13 (Money, dépendance de séquencement) |

## Contexte

La table `garantie` (migration V1, EP-04 historique) modélise aujourd'hui un **instantané plat** : un montant, un statut (`DETENU`/`RESTITUE_PARTIEL`/`RESTITUE_TOTAL`), un `montant_retenu` et un motif. `GarantieService.restituer()` mute directement ces champs. Aucun historique de mouvement n'existe. Le PO souhaite transformer la garantie en véritable compte, où le solde est toujours recalculable à partir de mouvements typés, et où toute retenue sur impayé résulte d'un choix explicite du gestionnaire (jamais d'un prélèvement automatique).

Un champ dupliqué historique a été identifié pendant l'analyse : `bail.depot_garantie` (colonne V1 sur `bail`), distincte de la table `garantie` — deux sources de vérité coexistent déjà aujourd'hui pour le montant de dépôt.

## Problème

1. Le solde de garantie n'est pas auditable mouvement par mouvement — seule la valeur finale est connue, sans trace du « pourquoi » entre deux états.
2. Le batch d'alertes (`V9`/`V10`, alerte `GARANTIE_NON_RESTITUEE`) lit directement `garantie.statut` et `garantie.bail_id` en `SECURITY DEFINER`/BYPASSRLS — toute évolution du modèle doit rester compatible avec cette lecture batch existante, sous peine de régression silencieuse d'un mécanisme d'alerte déjà en Production.
3. Les garanties existantes en Production doivent conserver leur histoire réelle (dépôt initial, restitutions déjà effectuées) au moment de la bascule vers le nouveau modèle — aucune perte de traçabilité tolérée.
4. Le PO souhaite préparer, sans la développer, l'architecture de fin de bail (restitution totale/partielle, retenues diverses).

## Décision

**Introduire `GarantieMovement` comme journal append-only, avec `Garantie.soldeActuel`/`statut` conservés comme colonnes de cache recalculées de façon transactionnelle et synchrone à chaque mouvement — jamais mutées directement.**

Principes retenus :

1. **`GarantieMovement`** (nouvelle table `garantie_movement`, RLS `bailleur_id` selon le pattern déjà utilisé pour `patrimoine`/V12) : `id, garantie_id, date, type, debit, credit, solde_apres, motif, utilisateur, commentaire, reference_document`.
2. **`TypeMouvementGarantie`** (enum Java + `CHECK` DB, pas de type ENUM Postgres natif — convention du projet) : `DEPOT_INITIAL, COMPLEMENT, RETENUE_LOYER, RETENUE_CHARGES, RETENUE_REPARATION, RESTITUTION, AJUSTEMENT, ANNULATION`.
3. **Cache transactionnel, pas de recalcul asynchrone** : `garantie.statut`/`solde_actuel` restent des colonnes physiques, recalculées dans la même transaction que l'insertion du mouvement — condition nécessaire pour que le batch d'alertes existant (`V9`/`V10`) continue de fonctionner sans modification de sa requête.
4. **Migration rétroactive obligatoire** : chaque garantie existante génère un mouvement `DEPOT_INITIAL` reconstituant son montant initial, et le cas échéant un mouvement `RESTITUTION`/`AJUSTEMENT` reflétant son état actuel (`RESTITUE_PARTIEL`/`RESTITUE_TOTAL`), afin que le solde recalculé égale exactement l'état actuel — vérifié un par un en Staging avant toute fusion.
5. **Aucun prélèvement automatique** : toute retenue sur impayé (`RETENUE_LOYER`) résulte d'une action explicite du gestionnaire (choix oui/non, puis montant), jamais d'une règle automatique déclenchée par un paiement en retard. Le mouvement, une fois créé, est relié au paiement concerné via une **FK nullable** `paiement.garantie_movement_id`.
6. **Traçabilité complète** : chaque mouvement déclenche un appel `AuditService.enregistrer(...)`, selon le pattern déjà en Production pour `EFFACEMENT_LOCATAIRE` (RgpdService).
7. **Fin de bail — préparation d'architecture uniquement (EP-13)** : le modèle `GarantieMovement` couvre nativement restitution totale (`RESTITUTION` avec `debit = solde_actuel`), restitution partielle (`RESTITUTION` avec `debit < solde_actuel`) et retenues diverses (`RETENUE_CHARGES`/`RETENUE_REPARATION`). Aucune nouvelle table n'est nécessaire. Un futur service d'orchestration (`ClotureBailService`) pourra composer plusieurs mouvements en une seule transaction métier — non développé à ce stade, simple point d'extension documenté ici.
8. **`bail.depot_garantie` — tranché au kickoff Sprint 9 (2026-07-02)** : devient une **valeur
   dérivée du ledger**, plus jamais saisie ni stockée sur `bail`. Conséquences d'implémentation :
   - La colonne `bail.depot_garantie` est **supprimée** (migration V20) — plus de double source
     de vérité entre `bail` et `garantie`/`garantie_movement`.
   - `BailRequest.depotGarantie` est retiré : le dépôt ne se saisit plus à la création du bail
     (aucun `Garantie` n'existe encore à cet instant — incohérence temporelle sinon), mais via le
     flux « Ajouter garantie » déjà existant (`POST .../garanties`).
   - `BailDto.depotGarantie` reste exposé dans l'API (pour ne pas casser les consommateurs
     existants) mais devient **calculé** : somme de `garantie.montant` pour toutes les garanties
     rattachées au bail (équivalent, à ce stade, à la somme des mouvements `DEPOT_INITIAL` +
     `COMPLEMENT` du ledger — `COMPLEMENT` n'étant pas encore exposé métier avant le Sprint 10,
     `garantie.montant` reste la valeur de référence pour l'instant ; à revisiter en Sprint 10
     quand `COMPLEMENT` deviendra utilisable, en calculant alors directement depuis
     `garantie_movement`). Un bail fraîchement créé (sans garantie) affiche `0`.

## Conséquences

- ✅ Solde toujours recalculable et audité, conforme à l'exigence « aucune modification directe du solde ».
- ✅ Aucune rupture du batch d'alertes existant si le cache `statut` reste synchrone (condition explicite ci-dessus).
- ✅ Prépare Fin de bail sans développement anticipé ni dette d'architecture.
- ⚠️ **Risque élevé** : migration rétroactive de données réelles de Production — nécessite un test complet en Staging avec vérification manuelle avant fusion (cf. Plan d'Exécution, Sprint 9).
- ⚠️ Postgres ne peut pas vérifier nativement la cohérence `bailleur_id` entre `paiement.garantie_movement_id` et `garantie_movement` — validation service-level requise (même pattern que `GarantieService.exigerBailDuBien` déjà en Production).
- ⚠️ Dépend de ADR-13 (`Money`) pour l'affichage devise-aware des montants de mouvement — séquencement Sprint 8 avant Sprint 9/10.

## Risques

| Risque | Niveau | Mitigation |
|--------|--------|------------|
| Migration rétroactive incorrecte (solde recalculé ≠ état actuel) | **Élevé** | Vérification ligne à ligne en Staging avant fusion ; test automatisé verrouillant l'invariant solde == somme des mouvements |
| Rupture du batch `GARANTIE_NON_RESTITUEE` si le cache `statut` n'est pas maintenu de façon synchrone | **Élevé** | Test de non-régression dédié du batch, critère GO bloquant Sprint 9 |
| Incohérence cross-table `bailleur_id` (paiement ↔ garantie_movement) | Moyen | Validation service-level explicite, test dédié |
| Duplication persistante `bail.depot_garantie` vs ledger si non tranchée | Moyen | Décision PO explicite exigée avant migration Sprint 9 |

## Alternatives écartées

| Alternative | Raison du rejet |
|-------------|-----------------|
| Recalculer le solde à la volée à chaque lecture (pas de colonne de cache) | Casserait la requête `SECURITY DEFINER` du batch d'alertes existant sans réécriture de celui-ci ; coût de performance non justifié pour un historique généralement court |
| Développer dès maintenant le processus complet de fin de bail (Évolution 7) | Explicitement hors périmètre demandé par le PO — « ne pas développer toute cette fonctionnalité maintenant, préparer uniquement l'architecture » |
| Table `garantie_movement` sans `bailleur_id` propre, en s'appuyant uniquement sur la jointure vers `garantie` | Casserait le pattern RLS `FORCE` direct utilisé partout ailleurs dans le projet (défense en profondeur : chaque table métier porte sa propre colonne `bailleur_id`, ADR-01) |

## Addendum — Sprint 10 (2026-07-03)

Point 8 exécuté : `GarantieRepository.sommeMontantDeposeParBail` est désormais recalculée depuis
`garantie_movement` (somme des crédits `DEPOT_INITIAL`+`COMPLEMENT`) au lieu de `garantie.montant`,
`COMPLEMENT` étant devenu utilisable métier (US-96). `BailDto.depotGarantie` reflète donc
désormais les réapprovisionnements. Aucune réécriture des points 7/8 ci-dessus — ce point les
complète sans les contredire.
