# Addendum EB — Gestion des personnes (Gestionnaires & Locataires durables)

| Champ | Valeur |
|-------|--------|
| Document de référence | `expression-besoin.md` v1.2 (✅ Validé — Gate 1 Go, 2026-06-04) — **non modifié** |
| Statut de l'addendum | **Proposé** — cadrage documentaire (analyse d'impact) ; aucun codage engagé. Kickoff clos (K1 tranché par le PO le 2026-07-08) ; GO explicite du PO sur le Plan d'Exécution requis avant Sprint A |
| Date | 2026-07-08 |
| Décision liée | D-PERS-001 (`docs/cgpa/05-architecture-conception/adr/ADR-16-gestion-personnes.md`) |
| Principe | Additif — n'invalide, ne rejoue ni ne modifie le Gate 1 Go déjà statué |

> Cet addendum étend le périmètre de l'EB v1.2 sans en altérer le contenu validé. Il introduit
> les besoins fonctionnels BF-97→BF-101 et précise §3 (parties prenantes) et §6 (matrice de
> rôles) pour la nouvelle granularité Gestionnaire/Locataire.

## 1. Extension du périmètre (EB §2.1)

Le périmètre inclus s'enrichit de :

- **Cycle de vie du compte Gestionnaire** : statut `ACTIVE`/`SUSPENDU`/`ARCHIVE`, distinct de la
  révocation d'`Affectation` déjà existante (BF-97).
- **Locataire comme entité de domaine indépendante** du `Bail` : statut `ACTIVE`/`ARCHIVE`,
  historique complet des baux successifs, conservation de tous les paiements/garanties/
  quittances/audits même archivé (BF-98).
- **Recherche multicritère** des Gestionnaires et des Locataires (BF-99).
- **Détection de doublons** à la création (email, téléphone, pièce d'identité pour le
  Locataire) (BF-100).
- **Écrans Historique** dédiés Gestionnaire et Locataire (chronologie, affectations/baux,
  paiements, garanties, archives) (BF-101).

> **Précision, pas contradiction, de la décision Gate-1 (§3)** : le Locataire reste
> exactement ce qu'il était — un « sujet de données, non-utilisateur au MVP ». Cette évolution
> ne lui donne ni identité Keycloak, ni rôle RBAC, ni connexion. Elle transforme uniquement son
> support de stockage (d'un champ texte embarqué dans `Bail` vers une entité de domaine
> durable), dans le même périmètre RGPD qu'aujourd'hui (ADR-03, précisé par ADR-16 D6).

> Ne reconduit **pas** d'exclusion EB §2.2 : aucun élément du périmètre exclu (quittance PDF,
> mandat, IRL, rapprochement bancaire, multi-bailleur/SCI, paiement en ligne, alertes e-mail,
> frais exceptionnels, application mobile) n'est concerné par cette évolution.

## 2. Nouveaux besoins fonctionnels

| ID | Besoin | Priorité | Lié à |
|----|--------|----------|-------|
| BF-97 | Le bailleur peut faire évoluer le statut d'un compte **Gestionnaire** (créer/compléter son profil métier, modifier, **suspendre**, **réactiver**, **archiver**, **restaurer**), le statut étant **global** au compte (partagé entre tous les bailleurs qui l'emploient — cf. ADR-16 D1). | Must | Règle métier PO 2026-07-08 |
| BF-98 | Le **Locataire** devient une entité indépendante du `Bail`, avec statut `ACTIVE`/`ARCHIVE` ; le même Locataire peut avoir plusieurs baux successifs (jamais plusieurs baux actifs simultanés sur le même bien — déjà garanti par `uq_bail_actif`, EF-12) ; l'historique complet (baux, paiements, garanties, quittances, audits) est conservé même archivé. | Must | Règle métier PO 2026-07-08 |
| BF-99 | Recherche multicritère : Locataire par nom/téléphone/email/numéro de pièce d'identité ; Gestionnaire par nom/téléphone/email. | Should | Règle métier PO 2026-07-08 |
| BF-100 | À la création d'un Gestionnaire ou d'un Locataire, le système avertit d'un doublon probable (email, téléphone, et pièce d'identité pour le Locataire) sans bloquer la création (avertissement, pas un rejet automatique). | Should | Règle métier PO 2026-07-08 |
| BF-101 | Écrans **Historique Gestionnaire** et **Historique Locataire** : chronologie des événements, affectations (Gestionnaire) ou baux (Locataire), paiements, garanties, statuts d'archive. | Should | Règle métier PO 2026-07-08 |

## 3. Parties prenantes — précision de l'EB §3

| Partie prenante | Rôle (inchangé) | Précision apportée par EP-15 |
|---|---|---|
| **Locataire** | Sujet de données (non-utilisateur au MVP) | Devient une entité de domaine persistante et durable (statut, historique), **reste non-utilisateur** : aucune connexion, aucune identité technique |
| **Gestionnaire mandataire** | Utilisateur délégué (invité) | Le compte gagne un cycle de vie propre (suspension/archivage), **global** entre bailleurs — le rôle et le mode d'invitation restent inchangés (ADR-10) |

## 4. Matrice de rôles & permissions — extension de l'EB §6

| Action | Bailleur | Gestionnaire |
|--------|----------|--------------|
| Modifier le profil d'un Gestionnaire (téléphone/photo/observations) | ✅ (tout bailleur ayant une relation, active ou passée, avec ce Gestionnaire — cf. ADR-16 D1/RSV-EP15-01) | ❌ |
| Suspendre / réactiver / archiver / restaurer un Gestionnaire | ✅ (idem — portée globale) | ❌ (**un Gestionnaire n'administre jamais un autre Gestionnaire** — règle métier PO, inchangée par cet Epic) |
| Créer / modifier un Locataire | ✅ | ❌ |
| Archiver / restaurer un Locataire | ✅ | ❌ |
| Consulter l'historique d'un Locataire/Gestionnaire | ✅ (son périmètre) | ❌ |
| Sélectionner un Locataire pour un nouveau bail | ✅ | ✅ (biens affectés actifs, comme pour la création de bail aujourd'hui) — **un Locataire archivé n'apparaît pas dans cette sélection** (BF-98) |

## 5. Hypothèses à valider (complète EB §9)

- [x] La portée **globale** du statut Gestionnaire (D1, ADR-16) est un choix PO assumé : tout
  bailleur ayant une relation avec un Gestionnaire partagé peut le suspendre/archiver, avec
  effet pour les autres bailleurs qui l'emploient. **Tranché par le PO le 2026-07-08.**
- [x] Sémantique de « créer » un Gestionnaire (K1, ADR-16) : **profil sur compte existant** —
  tranché par le PO le 2026-07-08 ; l'invitation reste l'unique voie de création technique.
- [ ] La détection de doublons est un **avertissement non bloquant** (BF-100) : à confirmer
  qu'aucun cas ne doit être un rejet strict (ex. deux Locataires avec exactement le même numéro
  de pièce d'identité chez le même bailleur) — proposition par défaut : avertissement partout,
  aucun rejet automatique, cohérent avec le principe énoncé par le PO (« produire des
  avertissements avant création »).

## 6. Non-régression

Aucune des hypothèses validées de l'EB v1.2 (§9) n'est remise en cause. Le scope EB §2.2 reste
inchangé et hors périmètre. La matrice de rôles §6 existante (bailleur/gestionnaire sur
biens/baux/paiements/garanties/honoraires/affectations) n'est pas modifiée — seules les lignes
relatives à l'administration des Gestionnaires/Locataires sont ajoutées (§4 ci-dessus).
