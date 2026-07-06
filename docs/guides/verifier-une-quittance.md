# Vérifier l'authenticité d'une quittance de loyer LoyerTracker

Ce guide s'adresse à toute personne (locataire, bailleur, CAF, banque, administration) qui reçoit
une quittance de loyer LoyerTracker et souhaite s'assurer qu'elle est **authentique et non
modifiée**. Aucun compte n'est nécessaire.

## En pratique

1. **Scannez le QR code** imprimé en bas de la quittance avec l'appareil photo de votre téléphone,
   ou saisissez l'adresse qu'il contient dans un navigateur. Elle a la forme :
   `https://loyertracker.loyerpro.org/verify/receipt/<identifiant>?token=<jeton>`.
2. La **page de vérification** s'ouvre et affiche un verdict :
   - **✓ Quittance authentique** — le document correspond exactement à l'exemplaire officiel
     conservé par LoyerTracker. La page affiche le bailleur, le locataire, le logement, la période,
     les montants, la date d'émission et l'empreinte du document.
   - **❌ Quittance non authentifiée** — le lien ne correspond à aucune quittance valide (QR
     modifié, document falsifié, ou lien tronqué). N'acceptez pas le document en l'état.
3. Depuis la page, le bouton **« Télécharger le PDF officiel »** vous permet de récupérer
   l'exemplaire de référence, servi uniquement s'il est intègre.

## Bon à savoir

- **Statut en temps réel.** Une quittance peut être marquée **Remplacée** (une version corrigée
  existe — la page indique son numéro) ou **Annulée** (elle n'a plus de valeur). Le verdict reflète
  l'état courant, même si le papier est ancien.
- **La moindre modification invalide la preuve.** L'authenticité repose sur une empreinte
  cryptographique du contenu : changer un montant, un nom ou une date sur le PDF le rend
  immédiatement « non authentifié ».
- **Confidentialité.** La page publique n'affiche que les informations nécessaires à la
  vérification. Le mode de paiement et les détails internes n'y figurent jamais.
- **Un QR sans page qui répond ?** Le service peut être momentanément indisponible : réessayez un
  peu plus tard. Un verdict « non authentifié » n'est jamais dû à une panne — il est explicite.
