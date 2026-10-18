# Briefing para Gemini Argon — Responsividade dos menus do Refúgio

## Objetivo

Tornar os menus customizados do mod proporcionais e legíveis em diferentes resoluções e valores de **GUI Scale** do Minecraft. Preservar o tema medieval (pergaminho, madeira, placa, botões e hover dourado), a fonte nativa `minecraft:uniform`, as ações existentes e o protocolo cliente-servidor.

O foco principal é a árvore de habilidades. A tela de diálogo já possui um cálculo proporcional e pode servir como referência, embora também deva ser verificada em resoluções extremas.

## Arquivos principais

### Telas customizadas ativas

- **Árvore de habilidades, desenho e hitboxes:** `src/client/java/com/felpz/refugio/client/RefugioAbilityScreen.java`. É a tela ativa da árvore. Desenha categorias, subcategorias, habilidades paginadas, detalhes, saldo, X, botões de compra/ativação, hover, textos e paginação. Também processa cliques e scroll. Renderização e hitboxes devem usar as mesmas medidas calculadas.
- **Diálogo dos NPCs, desenho e hitboxes:** `src/client/java/com/felpz/refugio/client/RefugioDialogueScreen.java`. Desenha pergaminho, moldura, título, retratos, texto rolável, barra de rolagem e botões de ação/fechar. O layout é calculado em `computeLayout()` e guardado no record `Layout`.
- **Roteamento das telas no cliente:** `src/client/java/com/felpz/refugio/client/RefugioClient.java`. Recebe os payloads `ABILITY` e `DIALOGUE`, abrindo ou atualizando as telas acima.

### Lógica que uma tarefa visual deve preservar

- **Ações de interface e sessões do servidor:** `src/main/java/com/felpz/refugio/ui/RefugioUiService.java`. Compras, ativação/desativação, fechamento, diálogos e ações dos NPCs são processados no servidor. Uma tarefa restrita a layout não deve reescrever essa classe nem mudar os payloads `BUY`, `TOGGLE`, `ABILITY_CLOSE`, `DIALOGUE_ACTION` ou `DIALOGUE_CLOSE`.
- **Dados, trilhas, nomes, descrições e ícones de habilidades:** `src/main/java/com/felpz/refugio/ability/RefugioAbilityData.java`. Os ícones da árvore são itens vanilla identificados por valores como `minecraft:iron_sword`, `minecraft:bow` e `minecraft:shield`, não PNGs individuais. Não alterar IDs, custos, pré-requisitos ou efeitos para resolver um problema visual.
- **Traduções:** `src/main/resources/assets/refugio/lang/pt_br.json` e `src/main/resources/assets/refugio/lang/en_us.json`. PT-BR é o idioma principal; preservar as chaves de ambas as línguas.
- **Build:** `gradlew.bat` e `gradle.properties`.

> A classe `src/main/java/com/felpz/refugio/ability/RefugioAbilityMenu.java` existe, mas a tela customizada ativa é `RefugioAbilityScreen`, aberta por `RefugioClient`. Não confundir a classe de menu com a tela visual ativa.

## Versões configuradas

O `gradle.properties` atual declara Minecraft **26.2**, Fabric Loader **0.19.5** e Fabric API **0.161.0+26.2**. Confirme as APIs diretamente no repositório; não presuma que o projeto ainda usa Minecraft 1.21.1 ou exemplos de APIs antigas.

## Recursos visuais

Os PNGs ficam em `src/main/resources/assets/refugio/textures/gui/dialogue/`. Árvore e diálogos compartilham estes recursos:

| Caminho | Dimensão | Uso |
|---|---:|---|
| `parchment.png` | 1024 × 576 | Fundo de pergaminho |
| `wood_frame.png` | 1024 × 576 | Moldura de madeira, desenhada sobre o pergaminho |
| `title_plaque.png` | 512 × 219 | Placa de título |
| `button_primary.png` | 512 × 219 | Botão principal |
| `button_close.png` | 512 × 219 | Botão secundário/fechar |
| `portraits/baldur.png` | 512 × 683 | Retrato de Baldur |
| `portraits/dario.png` | 512 × 683 | Retrato de Dario |
| `portraits/eldrin.png` | 512 × 683 | Retrato de Eldrin |
| `portraits/kael.png` | 512 × 683 | Retrato de Kael |
| `portraits/marta.png` | 512 × 683 | Retrato de Marta |

## Como a escala e o desenho funcionam hoje

### Moldura, pergaminho e placa

As duas telas têm um helper `blitFull(...)`: ele desenha a textura inteira (UV de 0 a 1) no retângulo de destino informado. Isso **estica a imagem para preencher o retângulo**; não há transformação global da tela nem nine-slice. Moldura e pergaminho têm proporção 16:9, mas a árvore pode atribuir-lhes outro formato, deformando os ornamentos.

O pergaminho é desenhado primeiro, dentro de margens de aproximadamente **5,5% da largura** e **8,5% da altura** do painel; a moldura é desenhada por cima. A placa também é redimensionada ao retângulo definido para o título.

### Botões e hover

Os botões texturizados são renderizados com `graphics.blit(...)` no retângulo calculado. O recorte UV atual usa `u=0..1` e `v=0.28..0.72`: usa a faixa vertical central do PNG e a estica no botão. O hover é dinâmico, não uma imagem separada: camada dourada translúcida, borda interna clara, filete superior e losangos dourados. Botões desabilitados recebem uma camada escura.

Ao tornar responsivo, inspecione o recorte e confirme os parâmetros UV usados pela API configurada. Preserve a aparência e não substitua texto dinâmico por uma imagem estática.

### Ícones e detalhes feitos por código

- Os ícones das habilidades, categorias e subcategorias são itens vanilla desenhados por `graphics.item(...)` no tamanho natural do inventário, aproximadamente **16 × 16 pixels GUI**. As posições variam conforme o cartão, mas esses ícones não recebem escala proporcional própria.
- O ícone de moeda da árvore é desenhado com blocos coloridos (`fill`) numa área aproximada de **12 × 12**; não é PNG.
- O botão X da árvore é desenhado com retângulos dentro de um hitbox de **22 × 22**.
- Ícones pequenos dos botões de diálogo são desenhados por código, normalmente numa área de **14 × 14**.
- Os textos usam a fonte `minecraft:uniform`.

## Árvore de habilidades: layout atual e pontos frágeis

Em `RefugioAbilityScreen.java`:

- O painel é centralizado e limitado a no máximo **1040 × 720**, mas largura e altura são calculadas separadamente como `width - 32` e `height - 32`. Não existe proporção fixa para o painel.
- Há breakpoints diferentes: cabeçalho compacto quando a largura do painel é menor que `600`; layout compacto quando a altura do painel é menor que `430`; a grade passa de quatro colunas para duas quando a largura fica abaixo de `380`.
- Categorias aparecem numa grade 2 × 2. As subcategorias usam cartões de proporção geral equivalente, centralizando linhas incompletas.
- A lista mostra **4 habilidades por página**. Painéis estreitos usam 2 colunas × 2 linhas; painéis largos, 4 colunas × 1 linha. A paginação também aceita scroll sobre a grade.
- Ainda há várias coordenadas/dimensões fixas, por exemplo: recuos do cabeçalho, botão Voltar de 150 × 26, botões de detalhe com 30 px de altura, X de 22 × 22 e espaçamentos dos cartões. O modo compacto altera alguns valores, mas não todos.
- Detalhes têm renderizadores normal e compacto. O compacto põe rank, estado e preço numa faixa, desenha uma barra de rank e reserva o restante para descrição. Os botões ficam junto ao rodapé.
- Algumas posições de clique e de desenho são calculadas em métodos separados. Se o visual for movido sem atualizar o hitbox, o botão parecerá estar num lugar e será clicável em outro.

O GUI Scale altera a largura e altura **lógicas** entregues à tela. Como a árvore mistura dimensões fixas e breakpoints, o espaço lógico pode diminuir sem que fonte, ícones e alguns controles diminuam na mesma proporção. Uma simples escala global pode gerar sobreposições e dessincronizar texto, itens e hitboxes.

## Diálogo: abordagem proporcional existente

`RefugioDialogueScreen.computeLayout()` calcula um único record `Layout`, usado tanto no desenho quanto nos eventos do mouse. O painel é centralizado, ocupa até cerca de **82%** da área disponível, respeita proporção próxima de **1,68:1** e tem largura máxima de 620. Título, botões, área de texto, retrato e largura dos botões derivam dessas medidas.

O retrato só aparece quando o painel tem pelo menos 300 px de largura e o viewport tem pelo menos 70 px de altura. Sua altura fica entre 48 e 140; a largura é 3/4 da altura, preservando a proporção dos PNGs 512 × 683. Em telas apertadas, o retrato some e o texto aproveita a largura liberada. A rolagem, o recorte visual do texto e os hitboxes da barra são calculados em conjunto.

Esse record compartilhado entre desenho e interação é um bom padrão para a árvore, mas não é necessário copiar os números ou a proporção do diálogo.

## Recomendações para a implementação

1. Criar uma estrutura única de métricas para a árvore (por exemplo, `AbilityLayout`) calculada a partir da largura e altura lógicas. Ela deve fornecer retângulos para painel, cabeçalho, saldo, X, cartões, paginação, detalhes e botões.
2. Reutilizar esses mesmos retângulos na renderização e nos cliques/scroll. Evitar repetir fórmulas em `extractRenderState`, `mouseClicked` e `mouseScrolled`.
3. Definir área segura e breakpoints explícitos para telas largas, estreitas e baixas. Limitar a escala, manter fonte/controles legíveis e impedir que o painel ultrapasse as bordas.
4. Tratar separadamente os itens vanilla: redimensionar o retângulo do cartão não redimensiona automaticamente o `graphics.item(...)`. Se usar transformação gráfica, confirmar a API da versão 26.2 e manter texto, tooltip e hitbox coerentes.
5. Preservar a proporção das texturas ou usar nine-slice quando apropriado. Se mantiver estiramento, conferir cantos e ornamentos em diferentes formatos. Comparar o resultado antes de mudar PNGs ou UVs.
6. Preservar 4 habilidades por página, navegação categorias → subcategorias → habilidades → detalhes, compras, ativação/desativação, saldo, feedback, retorno, fechamento e hover dourado.
7. Não alterar missões, economia, dados/efeitos das habilidades, NPCs, payloads ou lógica de servidor para corrigir somente o visual.
8. Revisar PT-BR e EN-US caso novos rótulos sejam necessários; não apagar chaves existentes.

## Plano de teste

Compile com:

```powershell
.\gradlew.bat compileClientJava
.\gradlew.bat clean build
```

A compilação não confirma o resultado visual. Teste no jogo com GUI Scale em Auto e nos valores disponíveis, incluindo o mais alto, em telas baixas e largas. Abra categorias, subcategorias, lista de habilidades e detalhes; confira hover, setas, scroll, compra, ativação/desativação, retorno e fechar. Confirme que os elementos não cruzam a moldura e que cada hitbox coincide com o botão desenhado. Teste diálogo com retrato e sem retrato.

Trabalhe em branch/cópia, confira `git status` e revise o diff. Não sobrescreva alterações locais, faça commit ou push sem autorização.

## Prompt pronto para colar no Gemini Argon

> Você está trabalhando no repositório do mod Minecraft Refúgio. A tarefa é **somente tornar responsivos os menus customizados existentes**, principalmente a árvore de habilidades, em diferentes resoluções e valores de GUI Scale. Leia primeiro `AGENTS.md`, `gradle.properties`, as telas e os assets. A configuração atual indica Minecraft 26.2, Fabric Loader 0.19.5 e Fabric API 0.161.0+26.2; confirme as APIs no projeto e não use exemplos de versões antigas.
>
> Telas ativas: `src/client/java/com/felpz/refugio/client/RefugioAbilityScreen.java` e `RefugioDialogueScreen.java`. O roteamento está em `RefugioClient.java`; o processamento servidor está em `src/main/java/com/felpz/refugio/ui/RefugioUiService.java`. Texturas ficam em `src/main/resources/assets/refugio/textures/gui/dialogue/`. A árvore usa itens vanilla como ícones, definidos em `RefugioAbilityData.java`, e a fonte `minecraft:uniform`.
>
> Antes de editar, relate os problemas encontrados e proponha um plano curto. Depois implemente incrementalmente um layout único reutilizado por renderização e hitboxes. Mantenha margens seguras, proporção e legibilidade; evite fator global que dessincronize texto, renderer de itens e cliques. Preserve o pergaminho/moldura, botões e hover dourado. Verifique o recorte UV atual dos botões e como os PNGs são esticados. Não transforme a tela em screenshot.
>
> Não mude lógica de compra, ranks, economia, missões, NPCs, efeitos, payloads, IDs, fluxo de servidor nem o limite de quatro habilidades por página. Não altere `RefugioUiService` ou `RefugioAbilityData` a menos que seja indispensável e justificado; prefira limitar as mudanças à apresentação. Preserve PT-BR/EN-US.
>
> Valide com `gradlew.bat compileClientJava` e `gradlew.bat clean build`, revise o diff e informe cada arquivo alterado. Se não puder executar o jogo e testar visualmente diferentes GUI Scales, diga isso claramente e liste os casos para validação manual. Não faça commit nem push sem autorização.
