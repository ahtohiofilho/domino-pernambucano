# UI Screen Patterns — Dominó PE

## Objetivo

Este documento define a gramática de composição das telas do Dominó PE. Ele complementa os tokens, cores e componentes existentes no código e deve ser consultado antes da criação ou da alteração estrutural de qualquer tela.

A regra principal é: **o título faz parte da composição da tela; ele não deve assumir automaticamente o papel de uma app bar presa ao topo.**

A tela `PlayModeScreen` ("Jogar") é a referência visual atual para o posicionamento vertical do cabeçalho e para a relação entre cabeçalho e conteúdo.

---

## 1. Princípios globais

### 1.1 Fundo e identidade
- Preservar o fundo azul institucional e o pattern discreto já fornecidos pelo design system.
- Não criar paletas locais quando já existir um token semântico equivalente.
- Usar amarelo para ação primária, vermelho/verde/azul como acentos conforme a hierarquia existente.

### 1.2 Área segura
- Todo conteúdo deve respeitar `WindowInsets.safeDrawing`.
- Nenhum controle de navegação, ajuda ou configuração deve depender de coordenadas absolutas de tela.

### 1.3 Largura
- Respeitar a largura máxima definida pelo scaffold.
- Cards e grupos principais devem compartilhar o mesmo eixo e a mesma largura visual sempre que pertencerem à mesma família de tela.

### 1.4 Ritmo vertical
- Preferir os espaçamentos de `MaterialTheme.dominoSpacing`.
- Evitar grandes vazios sem função entre título e conteúdo.
- Evitar compactar título e primeiro bloco a ponto de parecerem um único componente.
- O equilíbrio deve ser óptico, não apenas geométrico.

---

## 2. Regra oficial de cabeçalho

### 2.1 Cabeçalho composto
Para telas normais de navegação, seleção, perfil e detalhe:

- o título deve ficar na faixa superior-intermediária da tela;
- a referência perceptual é a tela `Jogar`;
- a seta de voltar deve ficar alinhada verticalmente ao título;
- o primeiro bloco de conteúdo deve começar logo abaixo do título, com espaçamento controlado;
- o título não deve ficar colado à margem superior como uma app bar Android, salvo quando a tela exigir explicitamente esse comportamento.

No código, novas telas curtas ou médias devem preferir:

`DominoScreenLayout.Guided`

Esse layout cria uma âncora vertical estável para o cabeçalho, independente da quantidade de conteúdo.

A âncora oficial é `DominoGuidedHeaderTopInset = 112.dp`, aplicada após a área segura. Ela é uma métrica semântica do design system, não uma coordenada derivada de uma captura específica.

### 2.2 Cabeçalho top
`DominoScreenLayout.Top` é exceção, indicado para:
- telas longas e naturalmente scrolláveis;
- páginas documentais;
- formulários extensos;
- superfícies utilitárias em que a leitura começa no topo;
- casos em que manter o título permanentemente alto melhora a compreensão.

### 2.3 Conteúdo centralizado
`DominoScreenLayout.Centered` pode permanecer em telas existentes cuja composição completa já esteja visualmente equilibrada, mas não deve ser usado como mecanismo genérico para posicionar títulos. A altura resultante depende do tamanho do conteúdo e pode mudar quando cards ou campos forem adicionados/removidos.

---

## 3. Arquétipos oficiais de tela

### 3.1 Main Menu
Referência: `MainMenuScreen`.

Características:
- marca/título central;
- ações principais agrupadas;
- utilitários independentes nos cantos;
- perfil no canto superior esquerdo;
- configurações no canto superior direito;
- ajuda no canto inferior esquerdo.

O menu principal não usa o mesmo cabeçalho das telas de navegação.

#### Main Menu Identity Chip
Quando houver identidade disponível, o canto superior esquerdo usa uma identidade compacta no formato:

`( AF ) Antônio Filho`

Regras:
- foto de perfil, quando disponível, tem prioridade sobre qualquer fallback textual;
- sem foto, usar **até duas iniciais**: primeira + última palavra significativa do nome público (`Antônio Filho` → `AF`);
- nomes de uma única palavra usam uma inicial; partículas como `de`, `da`, `do` e equivalentes não contam como última palavra significativa;
- não usar três letras no avatar compacto: o nome já aparece ao lado e a repetição reduz legibilidade em tamanhos de 38–42 dp;
- se não houver identidade significativa, usar um ícone genérico de perfil e o rótulo localizado `Perfil`;
- nomes longos devem permanecer em uma linha com ellipsis;
- o conjunto inteiro é a área clicável que abre o Identity Hub;
- quando o Identity Hub estiver ativo, não manter um card grande `Conta` duplicando a mesma entrada no menu.

### 3.2 Action Selection
Referência: `PlayModeScreen` ("Jogar").

Características:
- título e seta pertencem ao mesmo bloco visual das opções;
- primeiro card aparece próximo ao cabeçalho, sem grande vazio intermediário;
- ações seguem hierarquia clara: primária primeiro, secundárias abaixo;
- título deve permanecer na faixa visual de referência mesmo se a quantidade de ações mudar.

Novas telas desse tipo devem usar `DominoScreenLayout.Guided`.

### 3.3 Profile / Detail Hub
Referência de composição: `IdentityHubScreen` a partir do C35.

Características:
- cabeçalho na mesma faixa visual das telas de seleção;
- avatar e identidade começam logo abaixo do cabeçalho;
- informações pessoais formam um bloco coeso;
- o fallback do avatar segue a mesma regra do menu: foto → até duas iniciais → ícone genérico;
- o hero de identidade deve ser seguido por seções de ações e estado; um avatar isolado no centro da tela não constitui um Hub;
- organizar as funções em blocos como `Perfil`, `Personalização` e `Conta`;
- linhas internas podem ser mais compactas que os grandes cards do menu, com ícone, título, texto secundário e chevron quando houver ação;
- ações ainda não disponíveis não devem parecer clicáveis;
- não centralizar avatar e identidade isoladamente no meio da tela;
- não separar o título do perfil com um grande vazio.

Usar `DominoScreenLayout.Guided`.

### 3.4 Form / Authentication
Características:
- título na faixa guiada quando o formulário é curto;
- para formulários extensos, usar `Top`;
- campos pertencentes ao mesmo fluxo devem permanecer agrupados;
- CTA principal deve ser inequívoco e visualmente dominante;
- mensagens de erro/status ficam próximas ao elemento que explicam quando possível.

### 3.5 Information / Rules / Settings
Características:
- `Top` quando o conteúdo é longo, textual ou scrollável;
- `Guided` quando houver poucas opções e a tela funcionar mais como menu do que como documento;
- não usar `Centered` apenas para preencher espaço vazio.

### 3.6 Post-match / Results
Características:
- resultado é o foco primário;
- estatísticas devem seguir a hierarquia competitiva do jogo;
- ações de saída/continuação ficam visualmente separadas do conteúdo de resultado;
- pode usar composição própria, mas deve reutilizar tokens, margens e navegação oficiais.

### 3.7 Gameplay
A mesa tem regras próprias e não deve ser forçada aos padrões de telas de menu. Safe areas, overlays e controles de partida seguem contratos específicos do gameplay.

---

## 4. Relação título → primeiro conteúdo

Para `Guided`:
- o cabeçalho ocupa uma faixa estável da porção superior-intermediária;
- usar `dominoSpacing.xl` como ponto de partida entre cabeçalho e primeiro bloco principal;
- ajustes menores são aceitáveis quando o componente tiver volume visual muito diferente de um card padrão;
- grandes vazios entre título e conteúdo precisam de justificativa funcional.

A posição não deve ser definida por pixels absolutos nem por frações instáveis de um container scrollável. O posicionamento deve usar a métrica oficial do design system.

---

## 5. Do / Don't

### Do
- usar `PlayModeScreen` como referência perceptual do cabeçalho;
- manter título, voltar e primeiro bloco visualmente relacionados;
- escolher o arquétipo da tela antes de escolher o layout;
- reutilizar `DominoScreenScaffold` e o design system;
- testar em aparelho quando houver mudança de composição.

### Don't
- tratar todo título como app bar;
- centralizar geometricamente um bloco pequeno só porque há espaço disponível;
- deixar metade da tela vazia entre título e conteúdo sem função;
- criar padding em pixels/dp arbitrários para "bater" uma captura específica;
- introduzir um novo padrão sem atualizar este documento.

---

## 6. Checklist obrigatório para telas novas

Antes de implementar ou aprovar uma tela:

1. Qual arquétipo desta documentação ela segue?
2. Qual tela existente é a referência visual mais próxima?
3. O cabeçalho é `Guided`, `Top` ou uma composição especial justificada?
4. O primeiro bloco está visualmente ligado ao título?
5. O espaço vazio tem função ou é consequência acidental do layout?
6. A tela reutiliza tokens/componentes existentes?
7. O comportamento em tela pequena/scroll foi considerado?
8. Houve QA físico quando a composição mudou?
9. Se houver identidade no menu, o avatar compacto usa foto ou até duas iniciais, e o nome aparece ao lado?
10. O Hub possui ações/estado abaixo do hero em vez de terminar no bloco de identidade?

---

## 7. Governança

Este documento é parte do contrato visual do projeto.

Atualizá-lo quando:
- surgir um novo arquétipo de tela;
- uma tela existente passar a ser a nova referência;
- uma regra de composição for alterada;
- um padrão recorrente deixar de ser exceção.

Uma nova tela não deve criar silenciosamente uma terceira interpretação para título, navegação ou espaçamento quando `Guided` ou `Top` já atenderem ao caso.
