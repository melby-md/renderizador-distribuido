# Java CPU path tracer

Um renderizador distribuído de cenas 3D em Java 17+ que implementa um path tracer de CPU. A cena é descrita em XML, enviada pela interface web e renderizada como uma imagem PNG.

## Compilar

Na raiz do projeto, execute:

```sh
javac -d . src/*.java
```

## Executar

Inicie o servidor HTTP e o distribuidor TCP com:

```sh
java tracer.Server
```

Inicie um ou mais workers TCP, cada um em outro terminal ou máquina:

```sh
java tracer.Worker
```

Para conectar um worker a outro host ou porta: `java tracer.Worker <host> <porta>`. Depois, abra [http://localhost:8080](http://localhost:8080), selecione um arquivo XML de cena e clique em **Render**. O arquivo `test.xml` contém um exemplo.

## Como funciona

- `Server.java` inicia o servidor HTTP (porta `8080`) e distribui jobs de renderização pelo TCP (porta `8081`).
- `Worker.java` conecta ao servidor TCP e renderiza as faixas de linhas recebidas.
- `index.html` fornece a interface para enviar a cena XML e mostrar a imagem resultante.
- `Scene.java` lê as configurações, os materiais e as esferas descritos no XML.
- `Renderer.java` traça os raios e calcula a iluminação, os reflexos e a cor dos pixels.
- `Math3D.java` implementa as operações vetoriais e auxiliares usadas pelo renderizador.
