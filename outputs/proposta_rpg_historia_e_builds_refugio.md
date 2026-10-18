# Refúgio: proposta de história, RPG e builds

## Visão do projeto

**Fantasia central:** o jogador transforma o Refúgio de um abrigo em uma base de resistência, decide como lutar e se prepara para enfrentar hordas que crescem em força e escala. A campanha mantém a progressão reconhecível de Minecraft — ferro, Nether, End — mas altera o significado dessas etapas com uma história própria e escolhas de build.

A referência funcional é a árvore de habilidades de Cyberpunk: **ramificações claras, requisitos entre habilidades, escolhas que mudam a forma de jogar e respec possível**. A intenção não é copiar a estética ou os personagens daquele jogo.

> Princípio de design: os poderes devem ampliar opções e identidade de jogo, não tornar inúteis armaduras, poções, encantamentos, construções ou trabalho em equipe do modpack.

**Suposição:** interpretei a frase final “lembrando que podemos combinar mods em...” como uma referência a combinar mods dentro do modpack. Se você pretendia dizer outra coisa, ajustamos a proposta.

---

## História: Ecos da Ruptura

### Premissa

O Refúgio foi criado sobre uma antiga rota de portais, construída para proteger uma comunidade durante uma guerra contra criaturas que vinham de uma fenda entre dimensões. A vila não é apenas um lugar seguro: foi erguida para **conter e estudar a Ruptura**.

Com o passar do tempo, os portais do Nether e do End começaram a reagir à fenda. Os zumbis do mundo exterior passaram a surgir em grupos cada vez maiores. Alguns ficam mais resistentes ou perigosos conforme as noites passam. O Ender Dragon não criou a Ruptura, mas seu poder está ligado à energia necessária para estabilizá-la.

### Três atos

1. **O Abrigo** — o jogador chega ao Refúgio, conhece seus habitantes e aprende a obter recursos, sobreviver às primeiras hordas e escolher uma especialização.
2. **A Fenda** — os avanços de ferro, obsidiana e Nether revelam que as hordas não são eventos aleatórios. Dário encontra registros sobre a rota dimensional; Marta ajuda a manter a comunidade alimentada; novos ataques põem a base à prova.
3. **O Coração da Ruptura** — a jornada pelo Ender Dragon oferece um componente para conter a fenda. A vitória conclui a campanha principal, mas não precisa remover as hordas: o jogador continua defendendo a base em um pós-jogo opcional.

Assim, derrotar o dragão é um clímax narrativo sem desligar o conteúdo do mod de hordas.

### Tom e identidade

- Mistério, sobrevivência e reconstrução — não uma paródia tecnológica de Minecraft.
- Pequenas histórias pessoais nos diálogos, nos itens encontrados e nos pedidos dos NPCs.
- O jogador ajuda uma comunidade, mas não é obrigado a seguir todas as missões secundárias.
- O Refúgio permanece como base segura; a pressão vem das expedições e das defesas no mundo externo.

---

## Elenco de NPCs

O Refúgio já tem o Lojista, o Cronista da campanha principal, Marta e Dário como responsáveis pelas linhas secundárias. Sugestão de identidade para o grupo:

| NPC | Função | Papel na história e no jogo |
|---|---|---|
| **Baldur, Mercador do Refúgio** | Loja e economia | Mantém a troca de recursos e ajuda a comunidade a se reerguer. |
| **Eldrin, Cronista do Fim** | Campanha principal | Registra os avanços vanilla e revela a história da Ruptura em capítulos. |
| **Marta, Guardiã da Horta** | Agricultura e provisões | Missões de comida e criação; ajuda a preparar expedições longas. |
| **Dário, Cartógrafo e Alquimista** | Exploração e preparação | Missões de exploração, poções e recursos de Nether/End. |
| **Kael, Mestre de Combate** | Habilidades e builds | Apresenta a árvore, oferece treino, explica requisitos e permite reorganizar a build. |
| **Nara, Sentinela da Muralha** | Hordas e defesa | Missões de defesa, relatos de ataques e recompensas ligadas a combater grupos de inimigos. |
| **Ivo, Ferreiro da Vigília** *(opcional, etapa posterior)* | Melhorias de equipamento | Converte materiais difíceis em melhorias controladas; não deve substituir a mesa de encantamento ou as bigornas. |

### NPC de habilidades: Kael

Kael pode ser um ex-guarda que perdeu parte de sua equipe na primeira grande horda. Ele não “dá superpoderes” gratuitamente: ensina o jogador a canalizar a energia residual da Ruptura por meio de treinamento, prática e fragmentos encontrados em expedições.

Sua interface mostraria:

- nível de combate e pontos disponíveis;
- árvore por ramos, requisitos e prévias dos efeitos;
- build ativa e habilidades aprendidas;
- custos e consequências de um respec;
- missão de treino para testar uma habilidade sem comprometer a build.

### Nara e as hordas

Nara transforma as hordas em conteúdo intencional: preparar barricadas, defender moradores, sobreviver por determinada duração, eliminar ameaças especiais e reparar pontos vulneráveis. **Não devemos presumir uma API específica do outro mod**: primeiro integraremos com objetivos que o Refúgio consegue reconhecer; depois, se o mod de hordas fornecer eventos ou uma API compatível, podemos acrescentar integração direta.

---

## Sistema de builds

### Recurso de progressão

Sugestão para a primeira versão: **Pontos de Técnica** (PT), persistidos por jogador.

- Ganhos por marcos da campanha principal, missões secundárias importantes e objetivos de defesa.
- Opcionalmente, uma pequena parcela por níveis de experiência, com limite para impedir farm infinito.
- O ganho por matar qualquer zumbi, sozinho, não deve ser a única fonte: pode incentivar farm repetitivo e conflitar com a economia do modpack.
- Cada personagem tem sua própria build; modo de jogo/dificuldade não deve apagar os pontos salvos.

O jogador pode aprender várias habilidades, mas não deve maximizar todos os ramos cedo. **Respec** deve ser possível no Kael por moedas e um recurso raro, com o custo exibido antes da confirmação.

### Árvore inicial: quatro ramos

Começar com poucos nós bem testados. Os valores abaixo são diretrizes de design, não números finais.

#### 1. Lâmina — combate corpo a corpo

- **Golpe treinado:** pequeno bônus ao dano de espada.
- **Abertura:** bônus limitado no primeiro golpe após um intervalo, evitando multiplicadores permanentes excessivos.
- **Quebra-guarda:** dano adicional contra inimigos resistentes, com tempo de recarga.
- **Contra-ataque:** bloquear no momento certo reduz dano e habilita um golpe breve.
- **Especialização:** escolher foco em espada ou machado; não receber os dois bônus máximos de uma vez.

#### 2. Reflexos — mobilidade e evasão

- **Passo leve:** redução moderada de dano de queda.
- **Arranque:** corrida inicial curta com recarga.
- **Salto impulsionado:** superpulo com recarga e limite claro de altura.
- **Pouso controlado:** reduzir ou anular dano de queda depois de ativar o salto, desde que esteja no tempo de recarga.
- **Especialização:** mobilidade para explorar ou evasão para sobreviver a cercos.

Superpulo não deve permitir atravessar limites do mapa, quebrar encontros ou alcançar áreas proibidas. A dimensão do Refúgio e suas fronteiras precisam continuar protegidas.

#### 3. Bastião — resistência e defesa de horda

- **Postura firme:** resistência pequena enquanto usa escudo.
- **Última reserva:** proteção emergencial com recarga quando a vida cai abaixo de um limite.
- **Âncora:** menos repulsão de ataques.
- **Guardião:** bônus defensivo temporário perto de aliados ou moradores durante objetivos de defesa.
- **Especialização:** tanque contra grupos ou protetor de equipe.

#### 4. Sobrevivente — preparação, ferramentas e alcance

- **Mira estável:** melhora de controle com arco, sem transformar o arco em arma automática.
- **Rastreador:** destacar ou localizar ameaças próximas somente em situações definidas.
- **Preparação:** bônus limitado ao usar poções ou consumir comida em missões.
- **Coletor:** pequeno benefício de recursos em objetivos específicos, nunca duplicação geral de drops vendáveis.
- **Especialização:** arqueiro/explorador ou apoio/logística.

### Regras de balanceamento

1. Separar bônus aditivos pequenos de efeitos de recarga; evitar multiplicar dano final por vários fatores.
2. Testar combinação de habilidade, encantamento vanilla, poção e efeitos concedidos pelo modpack.
3. Cada efeito deve ter descrição, requisito, custo, duração e recarga visíveis.
4. Efeitos passivos devem ser modestos; efeitos fortes devem exigir ativação ou condição.
5. O servidor é a autoridade: o cliente não decide dano, recarga ou concessão de pontos.
6. Considerar opções de configuração para desativar ou ajustar cada ramo se houver conflito com outro mod.
7. Não punir quem não escolhe uma build específica; todas as rotas devem ter uma forma plausível de sobreviver.

---

## Integração com o mod de hordas

O mod de hordas cria a pressão externa; Refúgio oferece objetivos e ferramentas para responder a ela.

### Missões da Nara

1. **Primeira Vigília** — sobreviver à primeira noite de horda; recompensa modesta e uma habilidade introdutória.
2. **Muralha de Emergência** — montar uma defesa simples com blocos e iluminação.
3. **Sem Recuar** — proteger um ponto durante uma onda.
4. **Ameaça Persistente** — enfrentar uma horda depois de ela ficar mais forte, se o outro mod expuser essa informação.
5. **Reparar e Reforçar** — trazer materiais para manter as defesas.
6. **Caçador de Alvos** — eliminar um inimigo especial, apenas se o mod de hordas tiver um critério identificável.
7. **Defesa em Equipe** — concluir um objetivo com outros jogadores, opcional em single-player.
8. **Vigília do Refúgio** — desafio repetível ou pós-campanha, com recompensa limitada para evitar farm excessivo.

A contagem de ondas e a identificação de inimigos especiais devem depender de integração técnica confirmada com o modpack. Se isso não for possível, substituímos por critérios robustos do próprio Minecraft, como sobreviver a um período, derrotar grupos de inimigos hostis ou entregar materiais.

---

## Ciclo de jogo proposto

1. O jogador recebe um sinal de perigo ou uma missão da Nara.
2. Prepara equipamentos, comida, poções e blocos com ajuda de Marta e Dário.
3. Escolhe uma abordagem coerente com sua build: avançar, proteger, explorar ou atacar à distância.
4. Enfrenta a horda ou o objetivo de expedição.
5. Volta ao Refúgio, resgata recompensas e recebe Pontos de Técnica em marcos selecionados.
6. Investe na build, ajusta a estratégia e se prepara para ameaças mais fortes.

Esse ciclo cria motivo para usar a loja, os NPCs e as habilidades sem tornar obrigatório comprar cada item.

---

## Plano de implementação seguro

### Etapa 1 — Consolidar o conteúdo existente

- Confirmar NPCs, missões principais e as duas linhas secundárias já adicionadas.
- Corrigir objetivos secundários que dependam de estatísticas acumuladas para que sejam claramente comunicados.
- Testar entregas, consumo, recompensas encantadas e persistência após sair e entrar no mundo.

### Etapa 2 — Protótipo de habilidades

- Adicionar Kael, pontos de técnica persistentes e uma interface simples.
- Implementar somente três habilidades iniciais: golpe treinado, passo leve e postura firme.
- Criar teste de respec e sincronização multiplayer.
- Balancear com os equipamentos, poções e encantamentos do modpack real.

### Etapa 3 — Hordas

- Inspecionar o mod de hordas efetivamente escolhido e verificar se oferece API, eventos ou configuração de integração.
- Criar missões da Nara com objetivos que possam ser detectados de modo confiável.
- Testar ondas e builds defensivas em condições reais do modpack.

### Etapa 4 — História e profundidade

- Adicionar registros, diálogos por capítulo e objetivos de campanha ligados à Ruptura.
- Expandir para os demais nós da árvore após confirmar que as primeiras habilidades funcionam e são equilibradas.
- Considerar Ivo e melhorias de equipamento apenas se houver uma lacuna que as mesas vanilla não cobrem.

### Decisão de arquitetura

Manter, no início, **um único mod para o jogador e módulos separados no código**: campanha, missões, habilidades, hordas, economia e mundo. Isso facilita instalar o modpack e evita duplicar configurações. Se o sistema RPG crescer a ponto de poder ser usado fora do Refúgio, podemos extraí-lo depois em um add-on opcional dependente do mod principal.

---

## Próxima decisão de design

Antes de codificar a árvore inteira, vale definir três regras com impacto real no equilíbrio:

1. **Como o jogador ganha Pontos de Técnica:** marcos/quests, níveis de XP ou combinação dos dois.
2. **Quanto poder cada ponto concede:** bônus pequenos e frequentes ou poucas habilidades mais marcantes.
3. **Se a build pode ser refeita livremente:** gratuita, por moedas ou por moedas mais um item raro.

Minha recomendação para prototipar: pontos principalmente por missões e marcos, efeitos iniciais modestos, e respec pago mas acessível. Assim as builds importam sem transformar a progressão em grind obrigatório.
