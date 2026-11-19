# NathBot — le bot Minecraft de Nath et Vavan

Mod **Forge 1.20.1** (client) : un bot autonome écrit de zéro, **sans Baritone**. Il s'inspire
de l'idée de Baritone, mais tout est fait maison : pathfinding A*, minage, construction,
combat, panneau web…

## Installation (CurseForge, instance « Nath et Vavan »)

1. Récupère le fichier `nathbot-1.20.1-forge-1.0.0.jar` :
   - soit dans l'onglet **Releases** du dépôt GitHub (`nathbot-latest`),
   - soit dans **Actions** → le dernier build → **Artifacts** → `nathbot-jar`.
2. Dans CurseForge : clic droit sur l'instance **Nath et Vavan** → **Open Folder** → dossier `mods`.
3. Mets le `.jar` dedans et lance le jeu (Forge 1.20.1, version 47.x).

> Si tu avais l'ancien `baritoneui_1.20.1_Forge.jar`, enlève-le : NathBot le remplace.

## Utilisation rapide

| Touche (modifiable dans Options › Commandes › NathBot) | Action |
|---|---|
| **P** | Ouvre le panneau du bot |
| **K** | Arrête le bot |
| **J** | Pause / reprendre |
| **C** (maintenir) | Zoom |
| (libre) | Site web, diamants, finir le jeu, détecteur de minerais, vision nocturne, « viens ici » |

- Commandes : `/nb <commande>` ou `#<commande>` dans le chat (`/nb aide` pour la liste).
- **Site web local** : http://localhost:8765 (bouton dans le panneau P, ou `/nb web`).
  Port modifiable dans les réglages. Il n'est accessible que depuis ton PC.
- Réglages : panneau P → Réglages, ou **Mods › NathBot › Config**.

## Ce que fait le bot

### Demandé
- **Aller chercher des diamants tout seul** (`/nb diamants 5`) : fabrique les pioches s'il faut
  (bois → pierre → fer), descend à la couche -58, creuse des galeries, repère les minerais,
  les mine et ramasse.
- **Finir le jeu** (`/nb finir`) : bois → pioches → nourriture → fer + four → diamants →
  seau, silex, briquet → obsidienne (en mine, ou eau sur de la lave) → portail → Nether
  (forteresse, blazes, perles) → yeux de l'Ender → triangulation du fort → portail de l'End
  → dragon. **Expérimental** : c'est très long et ça peut se bloquer, reste à côté.
- **28 constructions prêtes** + tes propres fichiers `.nbt` (blocs de structure) dans
  `.minecraft/nathbot/schematics`. S'il **manque des matériaux**, le bot va les chercher
  (bois, planches, pierre, terre, sable, verre, lingots, objets simples) ou te dit ce qu'il manque.
  En créatif il se donne les blocs ; avec les cheats il peut construire en `/setblock`.
- **Site web local** avec carte, inventaire, minage, constructions (aperçu 3D), fonctions,
  points de passage, console et réglages.
- **Touche P** pour le panneau, changeable dans les paramètres de Minecraft.

### Les ajouts (plus de 50)

**48 fonctions activables** (panneau P → Fonctions, ou site web) :
manger auto, outil auto, arme auto, armure auto, totem auto, réapparition auto, point de mort,
torches auto, MLG seau d'eau, protection des outils, replanter auto, dormir auto, jeter les
déchets, pas de pause hors focus, messages du bot, défense auto (monstres), alerte creeper,
alerte vie basse, sprint auto, marche sécurisée (bords), marche auto, anti-AFK, pêche auto,
vision nocturne, détecteur de minerais (à travers les murs), voir monstres / animaux / joueurs /
objets / coffres / spawners & portails / plantes mûres, zones de spawn, lignes vers les joueurs,
affichage du chemin, trace du trajet, zoom, HUD (état, coordonnées, coords Nether, infos, durabilité,
boussole, statistiques, trésors, vitesse), alerte inventaire plein, alerte nuit.

**28 constructions** : abri de survie, cabane, maison en bois, maison en pierre, maison moderne,
tour de guet, phare, château fort, rempart, pont, ferme à blé, enclos, puits, portail du Nether,
pyramide, igloo, dôme de verre, sphère, escalier en colimaçon, salle d'enchantement, bunker,
arène, labyrinthe, cœur géant, arbre géant, plateforme, fontaine, ferme à cannes à sucre.

**42 commandes** : goto (coordonnées, X/Z, couche Y, point), suivre, viens, explorer, surface,
tunnel, diamants, miner, récolter (mine/fabrique/cuit), fabriquer, cuire, construire, matériaux,
abri, portail, pos1/pos2 + sauver une zone en `.nbt`, recharger, finir, ferme, chasser, nettoyer,
ramasser, manger, dormir, trier l'inventaire, jeter, points de passage (ajouter/suppr/liste/boussole),
sethome/home, fonctions, activer/désactiver, régler, stats, pause/reprendre/stop, web, état.

## Sécurité du bot
- Ne creuse jamais à côté de la lave, vérifie sous lui avant de creuser vers le bas.
- Ne casse pas les coffres, lits, établis, fours… ni les blocs de la construction en cours.
- Se met en pause si la vie est basse (réglable).

## Compiler soi-même
```bash
./gradlew build
```
Le `.jar` est dans `build/libs/`. Le workflow GitHub Actions fait la même chose à chaque push.
