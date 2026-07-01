# ADR-12 — Extension des champs Patrimoine (EP-10)

| Champ | Valeur |
|-------|--------|
| Code de décision | **D-PAT-002** |
| Statut | **Proposée** — en attente de validation PO (Plan d'Exécution `plan-execution-evolutions-ep10-ep13.md`, Sprint 7) |
| Date | 2026-07-01 |
| Phase | 07 — Développement (lot post-`1.5.0`, continu) |
| Documents liés | `analyse-impact-evolutions-ep10-ep13.md` §1, `addendum-backlog-ep10-ep12.md` (US-90/91), ADR-11 (Patrimoine, décision d'origine) |

## Contexte

ADR-11 a introduit `Patrimoine` comme entité de premier niveau (EP-09, livrée et déployée). Le modèle actuel (`patrimoine` : `id, bailleur_id, nom, statut, date_creation, adresse`) couvre le regroupement de biens et l'archivage, mais pas les attributs géographiques/administratifs demandés par le PO pour qualifier plus finement un patrimoine (localisation précise, description libre, référence de gestion interne).

## Problème

1. Le PO souhaite rendre `nom` et `adresse` obligatoires. `nom` l'est déjà (`NOT NULL` depuis V12). **`adresse` est aujourd'hui nullable** (ajoutée par V16 comme champ optionnel) — la rendre obligatoire est un changement de contrainte, pas un simple ajout.
2. Le PO souhaite prévoir sept champs supplémentaires (`ville, commune, quartier, province_etat, pays, description, reference_interne`), tous optionnels.
3. Le champ `statut` (`ACTIF`/`ARCHIVE`) demandé par le PO **existe déjà** depuis ADR-11 — aucune action requise sur ce point.
4. Aucun écran CRUD Patrimoine dédié n'existe côté frontend (gestion inline dans le dashboard bailleur, avec un formulaire limité à `nom`/`adresse`, sans point de création UI) — cette évolution est l'occasion naturelle de statuer sur ce point, sans que cela soit strictement requis par la demande.

## Décision

**Étendre l'entité `Patrimoine` par sept colonnes optionnelles et durcir la contrainte sur `adresse`, sans introduire de nouvelle entité ni casser le modèle EP-09.**

Principes retenus :

1. **Migration en deux temps sur `adresse`** : (a) vérifier en Production le nombre de patrimoines avec `adresse IS NULL` ; (b) si non nul, backfill (valeur de reprise à définir avec le PO, ex. `"Adresse à renseigner"` marquée pour correction manuelle) puis `ALTER COLUMN adresse SET NOT NULL`. Aucune perte de patrimoine existant tolérée.
2. **Sept colonnes optionnelles** (`VARCHAR`, tailles alignées sur `adresse`/`nom` — 255 caractères sauf `description` en `TEXT`) ajoutées par une seule migration additive, sans backfill nécessaire (nullable dès la création).
3. **`statut` inchangé** — aucune migration, aucune ADR nécessaire sur ce champ, déjà conforme.
4. **Décision différée au kickoff Sprint 7** : le PO tranchera si cette évolution inclut la livraison d'un écran CRUD Patrimoine dédié (avec création UI) ou se limite à l'extension du formulaire d'édition inline existant. Cette ADR ne préjuge pas de ce choix, qui est un arbitrage de périmètre frontend, pas d'architecture.
5. **Export RGPD étendu** : `RgpdService`/`PatrimoineDto` doivent exposer les nouveaux champs dans l'export bailleur, pour rester conformes au droit d'accès (cohérent avec US-70 déjà en Production).

## Conséquences

- ✅ Aucune rupture de RLS/ReBAC (ADR-01/ADR-02) : les nouvelles colonnes suivent le même cloisonnement `bailleur_id` déjà en place sur `patrimoine`.
- ✅ Additive au sens du schéma, à l'exception du changement de nullabilité de `adresse` — seul point nécessitant une vérification de données avant déploiement.
- ⚠️ Si des patrimoines Production ont `adresse IS NULL`, une décision de backfill devra être validée par le PO avant la migration (bloquant Sprint 7, cf. Plan d'Exécution).
- ⚠️ Le point de création UI manquant (constat, pas régression introduite par cette ADR) reste un arbitrage ouvert, sans impact sur le modèle de données.

## Risques

| Risque | Niveau | Mitigation |
|--------|--------|------------|
| Patrimoines Production avec `adresse` vide bloquant la migration `NOT NULL` | Moyen | Comptage préalable obligatoire avant tout commit de migration (critère GO Sprint 7) |
| Confusion avec le travail déjà livré EP-09 (double développement de la cardinalité Bailleur→Patrimoine→Bien, déjà en place) | Faible | Cette ADR ne touche que les champs, pas la structure relationnelle, déjà actée par ADR-11 |
| Périmètre frontend non tranché en amont (CRUD dédié vs extension inline) | Faible | Décision explicite au kickoff Sprint 7, documentée dans la clôture de sprint |

## Alternatives écartées

| Alternative | Raison du rejet |
|-------------|-----------------|
| Stocker les champs géographiques dans une table de référence normalisée (ville/commune/pays comme entités séparées) | Sur-ingénierie pour un besoin de saisie libre non contraint par le PO ; aucune règle métier de validation géographique n'a été demandée |
| Rendre `adresse` obligatoire sans vérification préalable des données Production | Risque de migration bloquante en Production sans filet de sécurité — rejeté pour non-conformité à la règle CGPA « aucune donnée existante ne doit être perdue » |
