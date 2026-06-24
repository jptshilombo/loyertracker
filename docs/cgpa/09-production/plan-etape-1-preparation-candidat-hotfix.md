# Plan détaillé — Étape 1 : Préparer le candidat Production du Hotfix

| Champ | Valeur |
|---|---|
| Date | 2026-06-24 |
| Statut | **Exécutée le 2026-06-24 — candidat recevable** |
| Plan directeur | `docs/cgpa/09-production/plan-execution-hotfix-production.md` |
| Type de livraison | Hotfix |
| Version retenue | `1.1.1` — patch SemVer |
| Candidat Staging documenté | commit `0adc494`, image `sha-0adc4941` |
| Production actuelle | `1.1.0`, image `sha-05424aa3` |

## 1. Objectif

Constituer un dossier candidat Production vérifiable pour le Hotfix patrimoine/bien, sans
exécuter le Gate Production, sans changer `PRODUCTION_READY` et sans déployer.

L'étape doit établir de manière certaine :

- le commit source exact ;
- l'existence des deux images GHCR immuables API et Web ;
- la réussite des contrôles CI, CodeQL et SonarQube applicables ;
- le périmètre fonctionnel exact du Hotfix ;
- la version SemVer et les documents de release ;
- le delta avec la Production actuelle ;
- le rollback applicatif prévu.

## 2. État d'entrée constaté

| Élément | Constat |
|---|---|
| Branche locale | `main` |
| HEAD local observé | `a33d103` |
| Candidat documenté et déployé en Staging | `0adc494` / `sha-0adc4941` |
| Écart à résoudre | Le HEAD local ne constitue pas à lui seul la preuve du commit distant candidat |
| Preuve Staging | 4/4 healthy, smoke 47/0, parcours navigateur réel réussi |
| CI documentée | CI et CodeQL verts sur `a281705` puis `0adc494` |
| Production actuelle | `sha-05424aa3` |
| Migration SQL du Hotfix | Aucune ; Flyway reste V1→V14 |
| Écart hors périmètre | Injection des variables CORS dans les fichiers Compose |

## 3. Périmètre inclus

- Correctif backend : création du patrimoine par défaut lors de l'inscription bailleur.
- Correctif frontend : sélecteurs patrimoine et type dans le formulaire bien.
- Correctif sécurité associé au candidat : jackson-databind 2.21.4.
- Métadonnées de release `1.1.1`.
- Preuves CI/CD et disponibilité des images.
- Comparaison avec la Production `1.1.0`.

## 4. Périmètre exclu

- Exécution du Gate Production.
- Sauvegarde ou accès en écriture à l'hôte Production.
- Pull ou déploiement des images sur Production.
- Correction du bug CORS Compose.
- Sprint 3 Patrimoine.
- Modification fonctionnelle ou reconstruction locale des images.

## 5. Sous-étapes d'exécution prévues

### 5.1 Identifier la source canonique

Contrôles en lecture seule :

1. synchroniser les références Git distantes sans modifier les fichiers ;
2. vérifier que `0adc494` appartient à `origin/main` ;
3. relever le SHA complet et les commits inclus depuis la base Production ;
4. confirmer que le candidat inclut `a281705` et le correctif jackson `0adc494` ;
5. expliquer toute divergence entre HEAD local, `origin/main` et candidat Staging.

Preuves attendues :

- SHA complet du candidat ;
- historique court `sha-05424aa3` → candidat ;
- liste des fichiers modifiés ;
- absence de migration Flyway V15+.

Critère d'arrêt : commit absent de `origin/main`, historique ambigu ou fichiers hors périmètre
non expliqués.

### 5.2 Vérifier les images immuables

Contrôles en lecture seule :

- vérifier l'existence de
  `ghcr.io/jptshilombo/loyertracker-api:sha-0adc4941` ;
- vérifier l'existence de
  `ghcr.io/jptshilombo/loyertracker-web:sha-0adc4941` ;
- relever leurs digests ;
- confirmer que Staging utilise ces mêmes références ;
- interdire l'usage de `latest`.

Critère d'arrêt : une image manque, le digest n'est pas accessible ou les images API/Web ne
proviennent pas du même candidat.

### 5.3 Vérifier CI, CodeQL et SonarQube

Contrôles en lecture seule :

- identifier les runs GitHub Actions du commit `0adc494` ;
- vérifier Backend, Frontend, Sécurité, Packaging Docker et CodeQL ;
- confirmer les Quality Gates SonarQube backend et frontend ;
- relever les éventuels reruns, flakes ou réserves ;
- vérifier que le job Packaging a réellement publié les images.

Critère d'arrêt : check requis rouge, annulé, absent ou preuve SonarQube indisponible sans
réserve explicitement évaluée.

### 5.4 Figer le périmètre et le delta Production

Produire une comparaison entre `sha-05424aa3` et le candidat :

- commits et fichiers applicatifs ;
- dépendances modifiées ;
- contrats API ;
- migrations SQL ;
- configuration Docker/infra ;
- secrets et variables d'environnement ;
- documentation.

Le delta attendu est limité au Hotfix patrimoine/bien, au bump jackson et à la documentation
associée. Tout autre changement doit être classé, expliqué et accepté ou provoquer le rejet.

### 5.5 Préparer les métadonnées `1.1.1`

Préparer sans publier :

- version cible `1.1.1` ;
- release notes dédiées `docs/release-notes-v1.1.1.md` ;
- entrée `CHANGELOG.md` ;
- identification `Hotfix`, commit, tag GHCR, source Staging et Production cible ;
- preuves de tests et limites connues ;
- rollback vers `sha-05424aa3`.

La modification des numéros de version applicatifs n'est requise que si la politique de release
du dépôt l'impose. Toute modification de code ou de build qui en résulterait exigerait un retour
CI/Staging et sortirait de cette simple préparation documentaire.

### 5.6 Produire le dossier candidat

Créer ou compléter :

- `docs/cgpa/09-production/release-candidate-v1.1.1-hotfix.md` ;
- `docs/release-notes-v1.1.1.md` ;
- le changelog ;
- la matrice des preuves CI/CD ;
- le delta Production ;
- la stratégie de rollback.

Le dossier doit porter le statut **CANDIDAT — Production non autorisée**.

## 6. Mutations autorisées pendant l'étape

Uniquement les écritures documentaires nécessaires au dossier candidat :

- release candidate ;
- release notes ;
- changelog ;
- Project State pour tracer le résultat de l'étape.

Aucun push, tag Git, release GitHub, changement de secret, opération Docker ou accès Production
en écriture n'est autorisé par ce plan.

## 7. Risques et mitigations

| Risque | Mitigation |
|---|---|
| Confondre HEAD local et candidat déployé | Prouver le commit via `origin/main`, CI et digests GHCR |
| Image documentée mais non publiée | Vérification explicite des deux manifests/digests |
| Inclure des changements non validés en Staging | Delta commit/fichiers et comparaison au tag Staging |
| Publier une version SemVer sans Gate | Documents marqués Production non autorisée |
| Mélanger le bug CORS au Hotfix | Exclusion explicite et risque séparé |
| Rejouer inutilement les tests | Utiliser les preuves du commit candidat ; tout nouveau changement impose une nouvelle CI |

## 8. Critères de recevabilité

Le candidat est **recevable** uniquement si :

- `0adc494` est confirmé comme commit canonique intégré à `origin/main` ;
- les images API et Web `sha-0adc4941` existent avec digests identifiés ;
- les checks requis et Quality Gates sont verts ;
- Staging a exécuté exactement ces images avec smoke 47/0 ;
- le parcours navigateur réel est tracé ;
- aucun changement non expliqué n'apparaît depuis `sha-05424aa3` ;
- aucune migration SQL n'est introduite ;
- release notes, changelog et rollback sont prêts ;
- le dossier reste explicitement non autorisé pour Production.

Dans tous les autres cas : **candidat rejeté** ou **préparation suspendue**.

## 9. Preuves de sortie

- SHA complet et arbre de commits.
- Digests GHCR API/Web.
- Tableau des checks GitHub et SonarQube.
- Delta Production classifié.
- Release candidate et release notes `1.1.1`.
- Rollback vers `sha-05424aa3`.
- Décision documentée : candidat recevable ou rejeté.

## 10. Point de décision

À la fin de l'exécution, présenter les preuves au PO et au Release Manager.

- **Recevable** : autorise uniquement la production du plan détaillé de l'Étape 2 — Gate
  Production accéléré.
- **Rejeté** : aucune suite ; correction du candidat puis nouveau passage Staging requis.

## 11. Résultat d’exécution

- Commit canonique : `0adc4941f854304a3f7412b04294615b05403707`, contenu dans `origin/main`.
- Images GHCR API et Web `sha-0adc4941` présentes avec digests identifiés.
- CI, sécurité, Packaging Docker et CodeQL : SUCCESS.
- SonarQube : Quality Gates backend et frontend OK.
- Delta applicatif limité au Hotfix et au correctif jackson ; aucune migration SQL ni modification Compose/infra.
- Version candidate : `1.1.1`, sans reconstruction d’artefact.
- Dossier : `docs/cgpa/09-production/release-candidate-v1.1.1-hotfix.md`.
- Release notes : `docs/release-notes-v1.1.1.md`.

## 12. Décision

**CANDIDAT RECEVABLE.**

L’Étape 1 est clôturée. Seule la production du plan détaillé de l’Étape 2 est désormais autorisée ; le Gate Production et le déploiement restent interdits à ce stade.
