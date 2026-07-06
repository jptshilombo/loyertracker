# Guide admin — Secret HMAC des quittances certifiées (`QUITTANCE_HMAC_SECRET`)

Public : exploitant / administrateur du déploiement. Ce guide décrit le rôle du secret HMAC qui
signe les QR de vérification des quittances (ADR-15 D3), sa configuration au déploiement et les
précautions de rotation.

## Rôle

Chaque quittance certifiée porte un QR contenant un **token HMAC-SHA256** lié au triplet
`(identifiant, version, empreinte du contenu)`. Le token n'est **jamais stocké en base** : il est
recalculé à la vérification à partir du secret. Une fuite de la base ne donne donc aucun token
valide ; à l'inverse, **le secret est l'unique clé de confiance** de tout le dispositif.

Variables d'environnement (host `.env`, jamais commitées) :

| Variable | Rôle |
|---|---|
| `QUITTANCE_HMAC_SECRET` | secret de signature (≥ 32 octets aléatoires) |
| `QUITTANCE_TOKEN_KID` | identifiant de version de clé, persisté avec chaque quittance (`token_kid`) |
| `QUITTANCE_VERIFY_BASE_URL` | base d'URL imprimée dans le QR (ex. `https://loyertracker.loyerpro.org`) |

## Configuration initiale (préflight bloquant)

1. Générer un secret fort :
   ```bash
   openssl rand -hex 32
   ```
2. Le renseigner dans le `.env` de l'hôte **avant** de démarrer la stack :
   ```dotenv
   QUITTANCE_HMAC_SECRET=<valeur générée>
   QUITTANCE_TOKEN_KID=1
   QUITTANCE_VERIFY_BASE_URL=https://loyertracker.loyerpro.org
   ```
3. Démarrer/redémarrer les conteneurs pour que l'API lise la valeur.

> ⚠️ **Sans secret configuré**, l'API génère un secret **éphémère aléatoire** au démarrage : les QR
> émis cessent d'être vérifiables au redémarrage suivant. La présence de `QUITTANCE_HMAC_SECRET`
> est un point **bloquant** de la checklist Gate Production.

## Rotation — état actuel et précautions

**Important.** La vérification utilise aujourd'hui **un seul secret actif**. Le `token_kid` est
persisté sur chaque quittance en prévision d'un trousseau multi-clés, mais la validation
**n'exploite pas encore** l'ancien secret : **changer `QUITTANCE_HMAC_SECRET` invalide donc tous
les QR déjà imprimés**.

Conséquences pratiques :

- **Gardez le secret stable** dans le temps. Ne le régénérez pas pour de la « bonne hygiène »
  périodique : une quittance papier doit rester vérifiable des années.
- **Ne rotez qu'en cas de compromission avérée** du secret. Dans ce cas, la rotation est un acte
  assumé : les documents antérieurs deviennent « non authentifiables » et doivent être ré-émis
  (nouvelle version) pour redevenir vérifiables sous le nouveau secret.
- **Sauvegardez le secret** hors dépôt, au même niveau de protection que les clés de production
  (perdre le secret = perdre la vérifiabilité de toutes les quittances).

> Évolution prévue (hors périmètre de cette release) : un trousseau indexé par `token_kid`
> permettra une rotation **sans invalider** les QR imprimés (vérification contre l'ancienne clé
> tant que des documents la référencent). Tant qu'elle n'est pas livrée, appliquer les précautions
> ci-dessus.

## Révocation d'une quittance

La révocation d'un document ne passe pas par le secret mais par son **statut en base**
(`ANNULEE` / `REMPLACEE`) : la page publique reflète l'état courant en temps réel, indépendamment
du token.
