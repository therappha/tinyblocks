# Performance: POC com muitos tinyblocks (issue #64)

Branch `perf/poc-many-tinyblocks`. POC: mexe no que mais pesava, sem reorganizar nada.

## Como medi

- **GameTest de custo** `SubgridManyPiecesCostGameTest` (estilo do `SubgridGearFieldCostGameTest`), com 4 cenários:
  - stone: um 16-grid com 2048 pedras e um 8-grid cheio (512);
  - cogs+stone: 16-grid com uma camada de 256 cogwheels + motor, debaixo de 6 camadas de pedra (1793 peças, 257 com tick);
  - redstone: 8-grid com chão e teto de pedra, alavanca → linha de dust → lâmpada, e um par de links (394 peças);
  - 9 grids 8 vizinhos, cada um meio cheio de pedra (2304 peças).

  Cada um loga uma linha `[many-cost]`: tempo de colocar, de um `serverTick` (média de 50–100), de um flip da
  alavanca, de uma query de outline e de colisão (via `BlockState`, média de 1000), e quantas faces um renderer
  desenha (`PieceFaceCulling.countDrawnFaces` vs 6 por peça).
- **JFR** no `runGameTestServer` (`settings=profile`), `jfr print --events jdk.ExecutionSample` e um script python
  que soma amostras por frame embaixo de `SubgridBlockEntity.serverTick`.
- **Client**: não dá pra rodar o jogo aqui. O culling é contado no servidor (mesma lógica que o renderer usa);
  o resto do render é raciocínio em cima do código.

Todos os números são de uma rodada só, com o lock do gradle só meu; variam uns ±30% de rodada pra rodada
(os de lever flip variam mais, JIT/GC).

## Antes → depois

| Cenário | Antes | 1. fake space lazy | 2. fake space por grid | 3. versão + 4. mesh |
|---|---|---|---|---|
| cogs+stone, tick | 22.6 ms | 1.8 ms | 38 µs | 29 µs |
| gear field 16 (256 cogs), tick | 4.7 ms | 1.5 ms | 18 µs | 17–39 µs |
| gear field 8 (64 cogs), tick | 436 µs | 237 µs | 4 µs | 3 µs |
| stone 16-grid, tick | 59 µs | 46 µs | 55 µs | 26 µs |
| redstone, alavanca on / off | 7.1 / 11.3 ms | 2.6 / 7.9 ms | 2.1 / 2.7 ms | 1.8 / 3.7 ms |
| colocar 2048 pedras | 595 ms | 101 ms | 107 ms | 82 ms |
| outline query, stone 16-grid | 25 µs | 21 µs | 18 µs | 1.35 µs |
| outline query, stone 8-grid | 6.4 µs | 5.6 µs | 5.8 µs | 0.33 µs |
| rebuild do outline, 256 cogs | ~220 ms | – | – | 167 ms |
| colisão (query) | ~0.1 µs | = | = | = (já era cacheada) |
| faces desenhadas, stone 16-grid | 12288 | | | 1024 |
| faces desenhadas, cogs+stone | 10758 | | | 2182 |
| faces desenhadas, 9 grids | 13824 | | | 2304 |

## O que mudou

1. **Fake space lazy** (`09b8078`). Todo fake space copiava o estado de todas as peças pro mapa de células
   (`populateCells`), todo call fazia `snapshot` de todas, e `applyChanges` comparava todas: O(n) por peça com tick,
   O(n²) por tick de grid. JFR do antes: 632 de 701 amostras de `VanillaBlockPiece.tick` em
   `populateCells`/`snapshot`/`applyChanges` (HashMap.put/resize). Agora célula não escrita cai no
   `realBlockStateAt` (que já lia o estado da peça, ao vivo), e o diff só olha as células escritas, na ordem do grid.
   Os dois lugares que usavam o mapa semeado como snapshot (`runItemInteraction`, `useItemOn` do teste) tiram um
   snapshot explícito. O "moved elsewhere" do piston continua olhando as outras peças, como antes.
   De brinde: `logResolve` formatava a descrição do `CellRef` mesmo sem debug.
2. **Um fake server space por grid** (`a177d4a`). Com (1) feito, o que sobrava era `FakeServerLevel.create`
   (construtor do `Level`): 55 de 72 amostras. Agora cada grid guarda um (`VanillaBlockPiece.TickSpace`), limpo e
   apontado pra peça antes de cada uso; uso reentrante ganha um novo.
3. **Contador de versão** (`4d8b3af`). Outline (toda query) e saída de redstone (todo tick) faziam hash de todas as
   peças. `pieceVersion` sobe em add/remove, `setChanged`, `notifyNeighbors` e no load do client. O outline das peças
   parciais agora junta em árvore balanceada.
4. **Mesh cacheado com culling** (`d6ed3bb`). `SubgridRenderer` fazia `renderSingleBlock` (lookup do modelo, getQuads,
   RandomSource) + `blockEntityFor` por peça por frame. Agora `StaticMesh` guarda os quads das peças MODEL por render
   type, refeito só quando `pieceVersion` muda (ou os modelos recarregam), sem as faces escondidas por vizinha no
   mesmo grid (`PieceFaceCulling`, mesma regra do `Block#shouldRenderFace`). Peça com block entity, fluido, outro
   render shape ou funil continua no caminho por frame.

Todos os GameTests passam (`All 96 required tests passed`) e os testes unitários também.

### Depois da revisão do PR #80

- O `TickSpace` agora é limpo (células escritas e ticks capturados, inclusive os do `FakeLevel` delegate do
  `blockEvent`) quando a peça o devolve, não só quando a próxima o pega: phantoms continuam presos nele entre ticks,
  e quem lê de fora do grid através deles (`boundPhantom`) via as escritas do último tick (ou uma peça já removida).
- `applyChanges` compara cada célula com o que ela tinha na primeira escrita da call (`FakeCellGetter.originalState`),
  não com o que tem quando o diff roda: uma escrita vinda de fora no meio (outro grid colocando peça aqui) não é
  desfeita.
- O crack não é mais desenhado duas vezes em peça com block entity e modelo baked (fornalha, hopper).
- GameTests novos (`SubgridTickSpaceGameTest`): leitura por phantom preso ao space depois de um tick (falhava antes
  do fix), e outline depois de uma peça mudar só de estado (alçapão aberto por alavanca).
- Medido de novo: tudo dentro do ruído (cogs+stone 34 µs, stone 16-grid 26–46 µs, alavanca 2.3 / 2.2 ms,
  outline 16-grid 1.3–1.8 µs).

**Ao juntar com o #74 (transform do #78):** `transform`/`turnMovedQuietly`/`lookAroundMoved`/`emptyQuietly`/
`loadMoved` precisam bumpar `pieceVersion` (via `markPiecesChanged`, `notifyNeighbors` ou `setChanged`). Qualquer
código que muda `runtimeState` sem passar por um desses deixa outline, saída de redstone e mesh velhos.

## O que ainda pesa

- **Render ainda é imediato**: o mesh cacheado ainda manda todos os vértices pro buffer todo frame (só que bem menos
  e sem lookup). Não testado no jogo — precisa de uma olhada visual (tint de grama/redstone, cutout de folha/vidro,
  crack, piston).
- **Outline de grid com muita peça parcial**: 256 cogs = ~170 ms pra reconstruir o `VoxelShape` (as coordenadas finas
  explodem o shape mesclado). Só acontece quando algo muda, mas num grid desses cada colocação trava.
- **Lever flip / cascata de redstone**: ~2–4 ms num grid de 394 peças. Cada `onNeighborChanged` ainda cria um
  `FakeLevel` novo (construtor do `Level`), e a cascata cria dezenas.
- **Colocar peças**: cada `placePiece` refaz o shape de colisão inteiro (gridSize³) e cria fake levels pro onPlace e
  pros vizinhos.
- Ticks em si agora são o tick do Create (`KineticBlockEntity.tick`), o que é o certo.

## Próximos passos

- Baker de chunk: `IDynamicBakedModel` no `SubgridBlock` lendo as peças via `ModelData`, pra mesh estático entrar no
  mesh da chunk section (cache e frustum culling do vanilla). Culling entre grids vizinhos do mesmo tamanho junto.
- Reusar `FakeLevel` (não-server) do mesmo jeito do `TickSpace` pra `onNeighborChanged`/`scheduledTick`/`randomTick`.
- Outline: não mesclar as peças parciais; fazer o `clip` por célula (GridRay) e desenhar só o outline da peça mirada.
- Colisão: atualizar o `BitSetDiscreteVoxelShape` incrementalmente em vez de refazer em cada add/remove.
- LOD de longe (item 6 do #64), se ainda precisar.
- Medir de verdade no client com Spark num mundo cheio.
