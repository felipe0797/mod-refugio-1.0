# Refúgio — documentação mestre e plano de evolução

**Objetivo:** servir de referência para continuar o mod Refúgio com o Gemini Argon. Este documento distingue o estado observado no código da visão futura; propostas não devem ser descritas como implementadas.

**Revisão:** 14/10/2026  
**Projeto-fonte:** `C:\Refugio\refugio-template-26.2`  
**Alvo:** Minecraft Java 26.2, Fabric, Java 25. Versões exatas do Loader/API: consultar `gradle.properties`.  
**JAR de desenvolvimento:** `build\libs\refugio-1.0.0.jar`

---

## 1. Estado atual — já existe no código

### Mundo e dimensão

- Dimensão personalizada `refugio:stonehill`, com mapa Stonehill instalado pelo mod no início do servidor.
- O mapa embutido fica em `src/main/resources/assets/refugio/stonehill_world/{region,entities,poi}`: nove arquivos MCA por pasta, 27 no total.
- Recorte nominal: raio de 600 blocos a partir do centro configurado `(-336, 224)`. Fora dos limites configurados, o jogador é reposicionado para dentro da área.
- Chegada configurada aproximadamente em `(-244.5, 76, 59.5)`.
- Hostis são bloqueados/removidos no Refúgio. As plantações próximas aos jogadores são mantidas hidratadas sem água.
- Baús com estoque inicial podem ser reabastecidos depois de ficarem vazios por cerca de dez minutos. O comando de reset atua sobre baús reabastecíveis carregados; não percorre todos os chunks da dimensão.
- Instalação de mapa: `world/StonehillMap.java`. Regras do mundo: `Refugio.java` e `world/VillageStructure.java`.

### Chave, dificuldade e economia

- A Chave do Refúgio é entregue ao jogador, tem descrição/saldo no tooltip, teleporta para a dimensão e armazena ponto de retorno.
- No primeiro uso, o jogador escolhe Fácil ou Normal.
- Fácil libera todas as categorias; Normal desbloqueia categorias por progresso na campanha.
- Moedas e dados do jogador são persistidos via Fabric Data Attachments.
- Loja de cinco categorias com catálogo vanilla selecionado/dinâmico, subcategorias, busca, paginação, detalhe de compra e confirmação.
- Venda por depósito: o jogador coloca itens na interface; shift-click transfere pilhas do inventário. O valor total aparece no painel/botão de esmeralda e há validação dos itens permitidos. Recompensas marcadas de missões não podem ser revendidas.
- Código econômico: `shop/ShopCatalog.java`, `shop/ShopPricing.java`; dados: `data/RefugioPlayerData.java`.

### Comandos existentes

- `/refugio admin resetar_baus` — restaura estoque de baús reabastecíveis atualmente carregados no Refúgio.
- `/refugio admin moedas <jogador> <quantidade>` — define saldo exato.
- Comandos administrativos usam a permissão `COMMANDS_ADMIN` do Minecraft.
- Registro em `command/RefugioCommands.java`.

### NPCs e missões

Há quatro aldeões protegidos, sem IA, identificados por tags e com interações registradas em `npc/RefugioNPCs.java`:

| Nome atual | Função implementada | Coordenadas | Profissão visual |
|---|---|---|---|
| Baldur, Mercador do Refúgio | Loja | `-274.645 84 100.843` | Ferreiro de ferramentas |
| Eldrin, Cronista do Fim | Campanha principal | `-288.306 84 107.596` | Bibliotecário |
| Marta, Guardiã da Horta | Missões secundárias agrícolas | `-282.678 88 122.700` | Fazendeira |
| Dário, Cartógrafo e Alquimista | Missões secundárias de expedição | `-279.675 90 132.639` | Clérigo |

As duas primeiras rotações correspondem às instruções prévias do usuário; rotações dos NPCs adicionais estão no código.

**Ainda não existe:** Kael, Nara ou Ivo como NPCs; cenas/dialogues; escolhas narrativas; tutorial de abertura entregue por conversa. As falas definidas adiante são propostas, não conteúdo atual.

#### Campanha principal — 8 missões

A sequência usa avanços vanilla e deve ser resgatada em ordem no NPC Eldrin. Implementação em `quest/RefugioQuests.java`.

| # | Objetivo | Recompensa implementada |
|---:|---|---|
| 1 | A Idade da Pedra — obter pedra | 40 moedas + 16 tochas |
| 2 | Era do Ferro — ferramentas de ferro | 60 moedas + peitoral de ferro |
| 3 | A Obsidiana e a Fagulha — avanço de obsidiana | 100 moedas + calça de diamante |
| 4 | Além do Portal — entrar no Nether | 200 moedas + 16 maçãs douradas |
| 5 | Caça às Chamas — obter vara de Blaze | 500 moedas + 20 Olhos do End |
| 6 | Rastro do Olho — seguir Olho do End/localizar fortaleza | 500 moedas + 12 Olhos do End |
| 7 | O Portal do Fim — entrar no End | 1.000 moedas + 64 foguetes com duração de voo 3 |
| 8 | O Último Desafio — derrotar o Ender Dragon | 10.000 moedas + 32 sopros do dragão |

O valor foi interpretado como **10.000** a partir da escrita antiga “10.0000”; reconfirmar se voltar a mexer na economia. No modo Normal, loja exige 0/1/2/4/8 missões resgatadas conforme a categoria. Há migração para o antigo progresso de sete missões. Recompensas ficam marcadas e protegidas da venda.

#### Trilhas secundárias — Marta e Dário

Há oito etapas independentes por trilha, progresso persistente por jogador e consumo de itens no resgate. Recompensas são mostradas quando concluídas/resgatadas e marcadas para não serem vendidas. Implementação em `quest/RefugioSideQuests.java`.

- **Marta:** trigo; alimentos cozidos; batata/cenoura; reprodução de animais; couro; avanço de armadura; carne podre; pão e tochas. Recompensas incluem pães, batatas, cenouras, cenouras douradas, maçãs douradas, guias, espada/picareta encantadas, farinha de osso e tochas.
- **Dário:** dormir; carvão e ferro bruto; lápis-lazúli e ouro; troca com aldeão; poção; distância de barco; avanço de detritos ancestrais; arco encantado. Recompensas incluem pão, tochas, picareta de ferro encantada, maçãs douradas, bloco de esmeralda, poções de resistência ao fogo/força, elmo de diamante encantado, Olhos do End, pérolas, machado encantado, maçãs douradas encantadas, flechas espectrais e livro Poder V.

**Ponto técnico para revisar:** objetivos com estatísticas cumulativas (dormir, trocar, distância de barco) ou avanços anteriores podem aparecer já completos quando desbloqueados. Se a intenção for contar apenas ações posteriores ao desbloqueio, registrar snapshots/flags de ativação ou usar eventos; comunicar progresso com clareza.

### Localização

- PT-BR e EN-US em `src/main/resources/assets/refugio/lang/`.
- Manter as chaves em paridade e colocar falas/descrições em arquivos de localização, não em strings fixas no código.

---

## 2. Cânone narrativo definido pelo usuário

### Premissa: Ecos da Ruptura

Os ancestrais construíram o Refúgio quando o mundo ficou perigoso por causa da quantidade de monstros. É abrigo e base de resistência, construído sobre uma antiga rota de portais associada a uma fenda dimensional chamada **Ruptura**.

A comunidade acredita que a energia do Ender Dragon poderá estabilizar as hordas. **Essa hipótese falha:** depois de derrotar o dragão, o jogador descobre que os zumbis evoluíram e conseguem usar a energia do Sol. Matar o dragão não encerra as hordas nem resolve a crise. A história continua em um pós-campanha de pesquisa, preparação e defesa.

### Arco narrativo

1. **O Abrigo:** jogador conhece o Refúgio, a vila, a segurança local e aprende a se preparar.
2. **A Fenda:** ferro, obsidiana e Nether revelam que as hordas não são ataques aleatórios. Dário encontra registros da rota de portais; Marta sustenta a comunidade; ataques ficam mais perigosos.
3. **A promessa do End:** Eldrin e Kael acreditam que derrotar o dragão possa estabilizar a Ruptura.
4. **A revelação solar:** após a morte do dragão, a horda continua. Sinais de zumbis mais fortes durante o dia mostram que os zumbis aprenderam a converter ou armazenar energia solar. O dragão não era a cura.
5. **Pós-campanha:** Refúgio desenvolve defesa, estudo e novas expedições. Futuras histórias podem investigar eclipses, zumbis solares ou como limitar a adaptação, mas não devem desligar o mod de hordas automaticamente.

A Ruptura explica o começo da crise; a adaptação solar explica por que ela já não pode ser simplesmente fechada. Não definir ainda uma cura final.

### História de Kael — Mago e Mestre de Combate

Kael é ex-guarda e único sobrevivente de sua equipe após a primeira grande horda. Com o tempo percebe mudanças e sensações estranhas dentro de si. Um dia, ao caminhar pela vila, ouve um sussurro chamando seu nome e segue a voz até uma caverna com luz azul.

No fundo há um objeto brilhante que parece ter caído do espaço, abrindo um buraco no teto. Ao aproximar-se, Kael é lançado contra a parede e começa a absorver a energia do objeto. Ganha poderes que ainda não compreende totalmente. Hoje pesquisa a Ruptura, pois a assinatura da energia do fragmento e a energia da fenda parecem iguais.

O fragmento explica as habilidades, mas não concede energia infinita: aprender e melhorar técnicas exige moedas, requisitos da árvore e equilíbrio.

---

## 3. NPCs planejados e diálogos

### Elenco

| NPC proposto | Função | Estado |
|---|---|---|
| **Baldur, Mercador** | Loja/economia | Implementado como NPC e interação; diálogos ainda pendentes |
| **Eldrin, Cronista** | Campanha principal | Implementado como NPC e interação; diálogos ainda pendentes |
| **Marta, Guardiã da Horta** | Agricultura/suprimentos | Implementada com trilha secundária |
| **Dário, Cartógrafo e Alquimista** | Exploração/poções | Implementado com trilha secundária |
| **Kael, Mago e Mestre de Combate** | Introdução, história e habilidades | Planejado, não implementado |
| **Nara, Sentinela da Muralha** | Hordas e defesa | Planejada, não implementada |
| **Ivo, Ferreiro da Vigília** | Melhorias de equipamento | Opcional para etapa posterior |

### Introdução no mundo inicial — ainda futura

Ao avançar alguns blocos no mundo inicial, Kael deve aparecer/iniciar conversa de forma única. Explica que o mundo ficou perigoso e que conhece um lugar seguro; entrega a Chave do Refúgio e convida o jogador a usá-la.

**Rascunho de fala:**

> “Ei, você! Não siga adiante sem olhar ao redor. O mundo mudou: as criaturas estão por toda parte, e depois do pôr do sol nenhuma estrada é segura. Há um lugar que ainda resiste — o Refúgio. Esta chave abre o caminho. Use-a quando estiver pronto; lá dentro explicarei o que sabemos.”

Hoje a chave é entregue pelo fluxo existente, não por encontro narrativo. A migração precisa manter saves e jogadores que já têm a chave, impedir duplicação e tornar a cena pulável/retomável. Definir como o NPC aparece em multiplayer sem duplicatas.

### Chegada ao Refúgio — ainda futura

Kael deve ficar perto do ponto de chegada, explicar que a vila é uma zona segura, mas que as hordas permanecem lá fora, convidar o jogador a visitar o castelo e conhecer os outros NPCs.

> “Você conseguiu. Este é o Refúgio, construído por quem se recusou a abandonar os outros. Aqui você pode respirar, planejar e se preparar. Mas lá fora as hordas continuam crescendo. Conheça a vila e depois me encontre no castelo: há algo na Ruptura que talvez explique o que está acontecendo.”

A posição de Kael no castelo e no mundo inicial ainda deve ser escolhida após inspecionar o mapa editado; não inventar coordenadas.

### Kael apresenta as habilidades — ainda futura

No castelo, Kael revela o fragmento azul, conta sua história e explica a árvore. O usuário quer que ele conceda **uma primeira habilidade como amostra**. Proposta: entregar uma única vez o rank 1 de uma habilidade básica; ranks seguintes custam moedas. Não repetir após morte, reconexão ou alteração do modo.

Cada NPC deve explicar sua função no primeiro contato e ter falas curtas que mudem conforme o progresso. Baldur explica compra/venda; Eldrin, campanha/resgates; Marta, entregas; Dário, expedição; Kael, builds; futura Nara, defesa. A interface de missões esconde recompensas antes da conclusão; a fala de resgate revela a recompensa.

---

## 4. Regras finais do sistema RPG (decisões do usuário)

### Moedas, ranks e ativação

- Habilidades serão compradas diretamente com as moedas existentes, não com pontos de técnica obtidos por XP.
- Para aprender o próximo rank, é necessário possuir o rank anterior e cumprir pré-requisitos de árvore. As missões não são bloqueio de compra.
- O jogador pode evoluir os quatro ramos em paralelo e ativar habilidades adquiridas dos quatro ao mesmo tempo.
- Aprendizados são permanentes. **Não haverá respec, reset nem reembolso.**
- Depois de aprender, o jogador pode habilitar/desabilitar cada habilidade individualmente. Esse toggle não devolve moedas.
- Persistir rank e estado ativo por jogador; compra e validação sempre no servidor.
- Kael dá uma única amostra gratuita conforme descrito na seção anterior.

### Custo por rank

Preço sugerido/solicitado por upgrade de cada habilidade: **rank 1: 500; rank 2: 1.000; rank 3: 2.000; rank 4: 3.000; rank 5: 4.000 moedas**. No texto revisado há uma observação “3x níveis”, mas a lista descreve cinco ranks; manter cinco como interpretação atual e confirmar se isso voltar a ser discutido. A leitura é que os custos se aplicam a cada habilidade, não globalmente à árvore.

### Ramo 1 — Lâmina

**Fio Crítico** (nome proposto para “Dano crítico”): cinco ranks; +10 pontos percentuais de chance de crítico com espadas por rank, máximo +50 pontos percentuais da habilidade. Definir precisamente o que é a chance-base e o teto antes da implementação. Servidor faz o sorteio; o bônus é para espadas, não deve vazar para machados/arcos.

**Impacto Crítico:** cinco ranks; +10% de dano ao golpe crítico por rank, máximo +50%. Separar chance de crítico do multiplicador do crítico e impedir multiplicação ilimitada com efeitos externos.

**Primeiro Corte** (evolução de “Abertura”): após tempo configurável sem causar/receber dano, o próximo golpe inicia uma abertura e dá bônus controlado. Ranks melhoram bônus ou tempo de recarga; não deve ser reativado a cada clique nem empilhar explosivamente com Fio Crítico.

**Retaliação** (evolução de “Contra-ataque”): escudo bloqueia ataque real; habilita janela curta para próximo golpe aplicar dano adicional. Ranks aumentam janela ou dano de forma moderada, com recarga. Validar o bloqueio no servidor.

**Maestria de armas:** linhas para espada, machado e arco, podendo aprender todas; dar identidades distintas e não tornar armas não escolhidas inúteis.

### Ramo 2 — Reflexos

- **Passo Leve:** ranks reduzem dano de queda; testar combinação com Feather Falling e outros mods.
- **Arranque:** aceleração curta, com ranks de duração/recarga, sem corrida infinita.
- **Ímpeto** (nome proposto para velocidade): velocidade de corrida até teto de **+30%**, por exemplo +6% por rank como ponto inicial a testar.
- **Salto ativo:** o jogador pode escolher entre superpulo e pulo duplo. Se possuir os dois, seleciona qual está ativo na interface; ambos podem ser aprendidos, mas não ativam ao mesmo tempo.
- **Regra de queda pedida:** registrar altura/camada de partida ao ativar salto. Se aterrissar na mesma camada ou acima, anular o dano de queda provocado por esse salto. Se aterrissar mais abaixo, aplicar dano normal pela queda abaixo da altura inicial. Limpar estado em troca de dimensão, morte, veículo, cancelamento ou outro estado inválido.
- Limitar altura/cargas; não permitir atravessar teto, fronteira do mapa, áreas vedadas ou encontros. Testar a regra em multiplayer e com efeitos externos.
- **Mineração:** adicionar ranks de mineração (posição sugerida em Reflexos ou Sobrevivente) para velocidade controlada com picareta, eficiência/durabilidade ou coleta; sem raio-X/revelação automática de minério e sem duplicar drops vendáveis.

### Ramo 3 — Bastião

- **Postura Firme:** redução escalonada até **20% máximo**, conforme pedido. Definir se aplica a todo dano ou só dano de combate hostil; recomenda-se começar por hostis e testar explosões, fogo, queda e efeitos externos.
- **Última Reserva:** quando vida cai abaixo de limite, concede proteção breve com recarga; ranks aumentam duração/recarga sem criar invulnerabilidade contínua.
- **Âncora:** reduz knockback gradualmente, sem removê-lo por completo.
- **Vigor da Horda** (evolução de “Guardião”): após derrotar inimigo, bônus temporário de dano até **+30%** no rank máximo solicitado. Definir duração e alvo; recomendação: renovar duração, não empilhar acima do teto.
- **Defesa contra grupos ou impacto em área:** os estilos podem ser aprendidos em paralelo, mas dano em área exige limite de raio/alvos e recarga para não eliminar hordas instantaneamente.

### Ramo 4 — Sobrevivente

- **Mira Estável:** controle do arco em ranks; sem disparo automático/dano ilimitado.
- **Rastreador:** detectar ameaças num raio e condições definidas; não revelar através de paredes/zonas seguras sem escolha explícita.
- **Preparação:** benefícios controlados ao usar poções ou comida; testar empilhamento com outros mods.
- **Coletor:** utilidade em objetivos específicos; não duplicar drops em geral nem abrir arbitragem compra→craft→venda.
- Trilhas de exploração/arqueiro e logística/apoio sem impedir comprar habilidades dos outros ramos.

### Balanceamento

- Cada efeito mostra rank, custo, requisito, limite, duração e cooldown.
- Separar bônus aditivos e multiplicadores; limitar empilhamento com encantamentos, poções e efeitos de outros mods.
- O servidor é autoridade para moedas, compras, recargas, crítico, dano e movimento.
- Preparar configuração para ajustar/desativar integração ou perks que conflitem com o modpack.
- Todas as builds devem continuar jogáveis; ninguém deve precisar comprar uma única árvore para sobreviver.

---

## 5. Modpack de hordas e Nara

O usuário pretende combinar o Refúgio com vários mods, incluindo um mod em que zumbis atacam em hordas e ficam mais fortes. O mod e a API exatos ainda não foram identificados.

- Não adicionar dependência obrigatória nem presumir nomes de eventos, classes, identificadores ou contagem de ondas.
- Primeiro identificar versão e documentação do mod realmente escolhido; procurar API/eventos públicos.
- Criar adaptador opcional se houver integração suportada. Refúgio deve continuar funcionando sem o mod.
- Enquanto isso, objetivos da Nara podem usar critérios detectáveis pelo próprio Refúgio/vanilla: defender ponto, sobreviver duração, derrotar inimigos hostis ou entregar materiais.
- Não prometer detectar “onda X” ou um zumbi de elite sem confirmação técnica.

**Nara, Sentinela da Muralha** (futura): Primeira Vigília; muralha emergencial; proteger ponto; sobreviver a horda reforçada se observável; reparar/reabastecer defesa; caçar alvo especial identificado; defesa cooperativa opcional; desafio de pós-campanha com recompensa limitada.

---

## 6. Ciclo de jogo pretendido

1. Kael encontra o jogador no mundo inicial, apresenta o perigo e oferece a chave.
2. Ao entrar, Kael explica o Refúgio e convida ao castelo.
3. Jogador conhece loja, campanhas, Marta e Dário; cada NPC explica sua função.
4. Kael narra a origem do fragmento, apresenta habilidades e concede uma amostra gratuita única.
5. Jogador compra ranks com moedas, ativa/desativa habilidades aprendidas e explora.
6. Enfrenta hordas/objetivos, vende recursos permitidos, cumpre missões e volta para aprimorar a build.
7. Derrota o Ender Dragon e descobre que as hordas persistem porque zumbis evoluíram e utilizam energia solar.
8. O pós-campanha sustenta a defesa e a investigação, em vez de retirar as hordas.

Somente recursos implementados devem ser descritos como atuais no jogo.

---

## 7. Arquitetura e regras para desenvolvimento

Manter um único mod instalável, com módulos internos. Não iniciar segundo mod nesta fase.

- `world`: dimensão, mapa, fronteiras e segurança.
- `npc`: spawn, tags, interações e encaminhamento.
- `dialogue`: estado por jogador, cenas, primeira conversa e falas localizadas.
- `quest`: campanha, trilhas secundárias, Nara.
- `ability`: catálogo de perks, preços, ranks, requisitos, estado ativo, cooldown e efeitos.
- `data`: moedas, progresso, tutorial, ranks e estados de ativação.
- `integration`: compatibilidade opcional/isolada com mod de hordas.
- `menu`: loja, missões e tela da árvore.

Regras: inspecionar `git status` e `AGENTS.md`; preservar alterações do usuário; pesquisar APIs reais 26.2; compras atômicas no servidor; migrar dados sem zerar saves; criar testes de custo/pré-requisito/teto/toggle; manter PT-BR e EN-US; não tocar em MCA durante mudança narrativa/habilidade; não fazer commit sem pedido.

---

## 8. Roadmap recomendado

### Fase 0 — Auditoria da base

- Conferir arquivos modificados e instruções locais antes de editar.
- Reexecutar testes/build atuais: ` .\gradlew.bat shopEconomyChecks build --no-daemon` (remover o espaço inicial do comando ao executar).
- Revisar objetivos cumulativos de Marta/Dário, resgate, persistência, itens marcados e duplicação.
- Corrigir somente problemas reproduzíveis antes de novos recursos.

### Fase 1 — Introdução e diálogos

- Estado de história persistente por jogador.
- Encontro único com Kael no Overworld; entrega protegida da chave; compatibilidade com jogadores antigos.
- Diálogo de chegada, convite ao castelo, apresentação curta dos NPCs e textos após resgate.
- Escolher posições de Kael depois de inspecionar a versão editada do mapa.

### Fase 2 — Modelo e interface de habilidade

- Modelo de dados: ID, ramo, rank máximo, custos, prerequisitos, efeito, duração, cooldown e estado ativo.
- GUI no Kael, saldo antes/depois, confirmação de compra e persistência.
- Sem respec/refund, sem gates por missão; toggle individual sem gasto/reembolso.
- Concessão de uma amostra gratuita uma só vez.

### Fase 3 — Protótipo técnico

- Implementar um rank inicial de três habilidades de referência: Fio Crítico, Passo Leve e Postura Firme.
- Testar com saves novos/existentes, multiplayer, morte, troca de dimensão, queda, encantamentos e efeitos do modpack.
- Após validar, completar os cinco ranks e demais nós.

### Fase 4 — Árvore completa

Adicionar Primeiro Corte, Retaliação, maestrias de espada/machado/arco, Ímpeto, superpulo/pulo duplo, mineração, Última Reserva, Âncora, Vigor da Horda, área, Mira, Rastreador, Preparação e Coletor. Cada habilidade deve ter limites e testes próprios.

### Fase 5 — Nara e hordas

Identificar o mod, versão e API; depois construir integração opcional e missões verificáveis, com fallback quando ausente.

### Fase 6 — Pós-End solar

Narrativa e missões após a revelação; continuar a sobrevivência sem desativar o mod externo. Futuras expansões podem explorar a energia solar.

---

## 9. Decisões a confirmar antes de implementar efeitos complexos

1. Qual é o mod de hordas e sua versão?
2. Coordenadas definitivas de Kael no Overworld/castelo após o mapa editado.
3. Qual habilidade será a amostra gratuita de rank 1?
4. Superpulo e pulo duplo: comprar ranks nos dois, alternar qual está ativo (recomendado), ou escolher um caminho permanentemente?
5. “+50% de chance crítica” significa +50 pontos percentuais de chance adicional ou aumento relativo a uma chance-base? Definir teto e interação com críticos vanilla.
6. Postura Firme reduz todo dano ou somente ataques hostis?
7. Vigor da Horda: duração do +30%, quais inimigos contam e o timer renova ou acumula?
8. Os custos de 500/1.000/2.000/3.000/4.000 aplicam-se a cada habilidade individual? Esta documentação assume que sim.
9. Manter apenas PT-BR e EN-US?

---

## 10. Prompt inicial para Gemini Argon

> Trabalhe no projeto existente em `C:\Refugio\refugio-template-26.2`. Leia este documento inteiro e inspecione o código antes de editar. A seção “Estado atual” descreve o que foi encontrado no código; Kael, habilidades, diálogos e integração com o mod de hordas são planos, não recursos existentes. Preserve alterações locais, mapa MCA e saves; verifique `git status` e `AGENTS.md` primeiro. O alvo é Minecraft 26.2/Fabric/Java 25; valide APIs no projeto.
>
> Não crie outro mod nem acrescente dependência obrigatória. Primeiro proponha um plano para Fase 0 e Fase 1, citando arquivos a alterar, riscos de migração e testes. Não edite antes de apresentar esse plano. O sistema futuro usa moedas existentes para comprar ranks permanentes; sem respec, reembolso ou gates por missão; quatro ramos podem evoluir em paralelo; habilidades aprendidas podem ser ativadas/desativadas individualmente. O Ender Dragon não encerra as hordas: o jogador descobre que zumbis evoluíram e utilizam energia solar.
>
> Quando autorizado a implementar, avance em fases pequenas, use strings PT-BR/EN-US, mantenha lógica sensível no servidor, crie/rode testes e build, reporte arquivos alterados e limitações. Não finja integração com mod de hordas ainda não identificado. Não faça commit sem solicitação.

---

## 11. Arquivos-chave

- `src/main/java/com/felpz/refugio/Refugio.java` — inicialização/dimensão.
- `src/main/java/com/felpz/refugio/world/StonehillMap.java` — instalação do mapa MCA.
- `src/main/java/com/felpz/refugio/npc/RefugioNPCs.java` — NPCs existentes.
- `src/main/java/com/felpz/refugio/menu/RefugioMenu.java` — loja/venda/telas atuais.
- `src/main/java/com/felpz/refugio/quest/RefugioQuests.java` — campanha principal.
- `src/main/java/com/felpz/refugio/quest/RefugioSideQuests.java` — trilhas de Marta e Dário.
- `src/main/java/com/felpz/refugio/data/RefugioPlayerData.java` — moedas, modo e progresso.
- `src/main/java/com/felpz/refugio/shop/ShopCatalog.java` e `ShopPricing.java` — catálogo e política econômica.
- `src/main/java/com/felpz/refugio/command/RefugioCommands.java` — comandos.
- `src/main/java/com/felpz/refugio/item/RefugioKey.java` — chave/teleporte.
- `src/main/resources/assets/refugio/lang/pt_br.json` e `en_us.json` — localização.
- `src/main/resources/assets/refugio/stonehill_world/` — mapa empacotado; não substituir sem pedido e backup.
