# Java CPU path tracer

Um renderizador de cenas 3D em Java 17+ que implementa um path tracer de CPU. A cena é descrita em XML, enviada pela interface web e renderizada como uma imagem PNG.

## Compilar

Na raiz do projeto, execute:

```sh
javac -d . *.java
```

## Executar

Inicie o servidor com:

```sh
java tracer.Server
```

Depois, abra [http://localhost:8080](http://localhost:8080), selecione um arquivo XML de cena e clique em **Render**. O arquivo `test.xml` contém um exemplo.

## Como funciona

- `Server.java` inicia o servidor HTTP e disponibiliza a página e a rota de renderização.
- `index.html` fornece a interface para enviar a cena XML e mostrar a imagem resultante.
- `Scene.java` lê as configurações, os materiais e as esferas descritos no XML.
- `Renderer.java` traça os raios e calcula a iluminação, os reflexos e a cor dos pixels.
- `Math3D.java` implementa as operações vetoriais e auxiliares usadas pelo renderizador.
