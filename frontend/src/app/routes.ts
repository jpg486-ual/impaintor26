import {Routes} from '@angular/router';
import {LobbyComponent} from './components/lobby/lobby.component';
import {GameComponent} from './components/game/game.component';

const routeConfig: Routes = [
  {path: '', component: LobbyComponent},
  {path: 'room/:code', component: GameComponent},
  {path: '**', redirectTo: ''},
];

export default routeConfig;
