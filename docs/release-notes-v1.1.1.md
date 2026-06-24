# Release Notes — LoyerTracker `1.1.1`

> Statut : **déployée et validée en Production le 2026-06-24 — `PRODUCTION_DEPLOYED`**.

## Correctifs

- Rétablit la création et la modification d'un bien depuis le tableau de bord bailleur.
- Ajoute le choix obligatoire du patrimoine et du type de bien.
- Crée un patrimoine principal actif lors de l'inscription d'un nouveau bailleur.
- Met jackson-databind à jour en 2.21.4 pour CVE-2026-54512/54513.

## Artefact candidat

- Commit : `0adc4941f854304a3f7412b04294615b05403707`.
- Images : API et Web `sha-0adc4941`.
- Base Production : `1.1.0`, `sha-05424aa3`.

## Validation

- CI, CodeQL, SonarQube et scans de sécurité : verts.
- Staging : 4/4 healthy, smoke 47/0.
- Parcours navigateur réel : création d'un bien via le formulaire Angular, réponse 201.
- Aucune migration SQL.

## Limites connues

Le défaut distinct d'injection des variables CORS dans les fichiers Compose n'est pas inclus.

## Déploiement technique

API et Web `sha-0adc4941` déployés le 2026-06-24 ; smoke Production 47/0, nettoyage complet et validation finale PASS.

## Gate Production

Gate Production accéléré : **GO sous réserve acceptée**, puis `PRODUCTION_DEPLOYED` le 2026-06-24.

## Rollback

Redéploiement du tag immuable `sha-05424aa3`. Sauvegarde pré-déploiement créée le 2026-06-24 : `loyertracker-20260624-140441.dump`, intégrité `pg_restore --list` OK. Déploiement et validation finale exécutés ; rollback conservé disponible.
