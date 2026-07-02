# Release Notes — LoyerTracker `1.6.0`

| Champ | Valeur |
|---|---|
| Version | `1.6.0` |
| Date de release | À déterminer (Gate Production en cours d'analyse) |
| Type | Release MINOR — Patrimoine enrichi (Sprint 7, EP-10) + Devise/Money (Sprint 8, EP-11) |
| Tag Production candidat | `sha-2da27182` |
| Release précédente | `1.5.0` — `sha-08b366fa` (2026-07-01) |
| Flyway | V18→**V19** (1 nouvelle migration) |

---

## Nouveautés

### Patrimoine enrichi (Sprint 7, EP-10, US-90)

Sept nouveaux champs optionnels sur `Patrimoine` : `ville`, `commune`, `quartier`,
`provinceEtat`, `pays`, `description`, `referenceInterne`. Le formulaire « Modifier un
patrimoine » du dashboard bailleur est étendu avec ces champs (US-91, extension inline — pas de
nouvel écran CRUD).

**`patrimoine.adresse` devient obligatoire** (`NOT NULL`, `@NotBlank` côté API) — elle était
nullable depuis la migration V16. Tout patrimoine existant sans adresse reçoit un backfill
générique (`"Adresse à renseigner"`) lors de la migration V19.

### Correctif devise réelle sur les documents locatifs (Sprint 8, EP-11, US-92)

Nouveau Value Object `Money(montant, devise)` (`com.loyertracker.baux.Money`) corrigeant un bug
réel : `DocumentHtmlBuilder.euros()` affichait systématiquement le suffixe « € » sur les
quittances et avis d'échéance, quelle que soit la devise réelle du bail (EUR/USD/CDF). Formats
d'affichage par devise (ADR-13) : EUR `800,00 €`, USD `$1,000.00`, CDF `1 000,00 CDF`.

### Devise affichée sur Paiements et Honoraires (Sprint 8, EP-11, US-93)

Les vues Paiements et Honoraires (bailleur et gestionnaire) affichent désormais la devise à côté
des montants, cohérent avec le dashboard Bail. `PaiementDto`/`HonoraireDto` exposent un champ
`devise` ; `MoneyFormatPipe` partagé côté frontend.

---

## Périmètre technique

| Aspect | Détail |
|---|---|
| Nouvelle migration | **V19** — 7 colonnes optionnelles + `patrimoine.adresse SET NOT NULL` (backfill générique) |
| Flyway après upgrade | 19/19 |
| API modifiée | `POST /api/patrimoines` : `adresse` désormais `@NotBlank` (rejet 400 si absente — rupture de compatibilité pour tout client externe qui omettait ce champ) |
| API ajoutée | `PaiementDto.devise`, `HonoraireDto.devise` (lecture seule, additif) |
| Rétrocompatibilité | Additive pour Sprint 8 (Money/Devise) ; **validation resserrée** pour Sprint 7 (`adresse` obligatoire en création) |
| Rollback schéma | Colonnes ajoutées + contrainte `NOT NULL` — voir `Compatibilité et rollback` ci-dessous |

---

## Compatibilité et rollback

- **Rollback applicatif seul** (retour à `sha-08b366fa`, `1.5.0`) : sûr pour les flux existants
  (le formulaire de création de patrimoine envoyait déjà `adresse` en pratique), **mais risqué
  pour l'inscription** — l'ancien `InscriptionService` (pré-US-90) créait le patrimoine par défaut
  **sans** `adresse`, ce qui échouerait avec une erreur 500 (violation de contrainte `NOT NULL`)
  tant que la migration V19 reste appliquée.
- **Conséquence** : si un rollback applicatif est nécessaire après déploiement de `1.6.0`,
  l'inscription de nouveaux bailleurs doit être considérée comme dégradée jusqu'à un correctif
  forward, sauf à restaurer aussi la base (`pg_restore` du backup pré-déploiement, annulant V19).
- **Rollback complet (schéma inclus)** : restauration du backup pré-déploiement obligatoire.

---

## Réserve portée depuis le Gate Staging

**RSV-S7-8-01** — confirmation visuelle USD/CDF (quittance PDF téléchargée + panneaux
Paiements/Honoraires de l'UI) non exécutée en Staging ; couverture jugée suffisante via
`MoneyTest`/`DocumentHtmlBuilderTest` (CI, 3 devises paramétrées). Recommandée avant ou pendant
la validation finale Production.

---

## Déploiement Production

| Étape | Statut |
|---|---|
| Gate Staging Sprint 7+8 | ✅ GO — STG-ISOL-01 PASS, Flyway 19/19, smoke 59/0 (2026-07-02) |
| Gate Production Sprint 7+8 | En cours d'analyse |
| Préflight + backup | À faire |
| Déploiement technique | À faire |
| Application adresse réelle Patrimoine (`PUT /api/patrimoines/{id}`) | À faire, immédiatement après déploiement |
| Smoke Production | À faire |
| `PRODUCTION_DEPLOYED` | À faire |
| Hôte | `loyertracker-prod-server` (`https://loyertracker.loyerpro.org`) |
