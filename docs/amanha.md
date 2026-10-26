# O que fazer amanhã (branch `feat/flywheel-create-experiment`)

Nada desta sessão foi commitado. O primeiro passo é revisar o `git status` e commitar o que funciona.

## Estado atual

### Funcionando (testado no jogo e com GameTest)
- **Funil conector** (`Shaft Connector`): eixo real ↔ eixo minúsculo pelo encaixe (a ponta do funil).
  - O funil afina do eixo grande até o eixo minúsculo, desenhado em fatias girando.
  - Clicar num eixo minúsculo com o conector coloca o funil na face, na ponta do eixo.
  - Clicar no funil com um bloco coloca o bloco no encaixe.
  - Código: `compat/create/` (`KineticBridge`, `ShaftConnectorPiece`, `ConnectorPlacement`) e `mixin/create/`.
- **Parada imediata:** quebrar o motor, a placa, um bloco real encostado ou o subgrid inteiro para a rotação na hora, nos dois lados.
- **Peso:** o subgrid pesa igual a um chassi no Sable (`mixin/sable/VoxelNeighborhoodStateMixin`).
- **Chave inglesa** em peças: girar funciona; shift + chave (desmontar) passa pelo item.
- `/tinyblocks frequency blue|red`: grava o item da mão num slot do link minúsculo. Isso funciona.

### Quebrado
1. **Redstone link dentro ↔ fora não acende a lâmpada**, nos dois sentidos, mesmo com frequências iguais. Você ganhou um achievement do Create, mas a lâmpada não acendeu.
   - Já existe: mixin da rede de links (`RedstoneLinkNetworkHandlerMixin`: rede pelo level real e alcance pela posição do SubgridBlock) e `LinkBehaviourMixin` (link minúsculo continua "vivo").
   - Testes que reproduzem (falhando): `tinyTransmitterPowersARealReceiver` e `realTransmitterPowersATinyReceiver` em `gametest/SubgridCreateInteractionGameTest.java`.
   - Próximo passo: pôr na mensagem de falha o que cada link enxerga (rede, `isAlive`, força transmitida/recebida, estado). Eu ia fazer isso quando paramos.
   - No `realTransmitterPowersATinyReceiver`, o link **real** some aos 3 ticks ("Missing block entity at 4,1,2"). Investigar por quê.
   - Lembrete do Create: um link novo nasce **transmissor** e só cria o behaviour de frequência no 1º tick. Um receptor criado direto como receptor nunca ganha frequência. Os testes já colocam como transmissor e depois trocam para receptor.
2. **Clicar no slot de frequência do link minúsculo não grava.** O teste `clickSetsATinyRedstoneLinkFrequency` falha.
   - O clique é repassado ao fake level por `v2/PieceClickDispatch`, com um FakePlayer no espaço virtual, mas o `LinkHandler` do Create não grava a frequência.
   - Próximo passo: logar se o evento chega ao `LinkHandler`, se ele acha o behaviour e o que o raio do FakePlayer acerta.

## Depois disso
3. **Lado do cliente do Create:** os contornos fantasmas dos slots (value boxes) quando você mira numa peça, e o scroll de valores (ex.: **velocidade do motor criativo**, que o carro precisa). Hoje a mira e os pacotes usam a posição real do SubgridBlock. Precisa de um mixin no cliente que troque pela peça mirada e a tradução dos pacotes no servidor.
4. **Carro:** testar o subgrid montado com o `/tinyblocks assemble` num sub-level do Sable: se a rotação continua dentro, e as rodas do Simulated ligadas pelo funil.
5. Revisar e commitar no branch do experimento (local, sem push).

## Comandos
- Testes de jogo: `./gradlew runGameTestServer` (roda todos; hoje 3 falham: os 2 do link e o do clique no slot).
- Teste unitário do funil: `./gradlew test --tests '*ShaftConnectorPieceTest' -x jacocoTestReport`
- Cliente: `./gradlew runClient`
