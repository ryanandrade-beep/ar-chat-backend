-- Dados fictícios para demonstração (Cliente Teste QR): contatos com etiquetas, atendimentos em datas
-- diferentes, mensagens e campanhas. Telefones começam com 5500000 (não existem) e nada vai para a fila de envio.
-- Para remover: limpar-dados-demo.sql
DO $$
DECLARE
    cid CONSTANT uuid := '32d33fdf-9f7c-47da-9a95-88fc86f30bb3';
    ops uuid[] := ARRAY['97b309f3-99a2-485b-8cf4-387b7f7bfe09', '76a9bf4f-ac4c-4d8e-aa0c-b757b5a17f84', '1c5ea0ad-d17b-4826-a8fd-2969426911fa']::uuid[];
    nomes text[] := ARRAY['Ana Beatriz Souza','Bruno Henrique Lima','Carla Mendes','Diego Ferreira','Eduarda Nascimento','Fábio Rocha','Gabriela Torres','Heitor Martins',
        'Isabela Carvalho','João Pedro Alves','Karen Ribeiro','Lucas Barbosa','Mariana Duarte','Nelson Teixeira','Olívia Castro','Paulo Vieira',
        'Quésia Moreira','Rafael Pinto','Sabrina Costa','Tiago Araújo','Úrsula Gomes','Vitor Cardoso','Wanda Monteiro','Xavier Dias',
        'Yasmin Freitas','Zélia Campos','Álvaro Rezende','Bianca Lopes','Caio Nogueira','Daniela Farias','Elias Batista','Fernanda Siqueira'];
    pool text[] := ARRAY['vip','centro','açougue','padaria','hortifruti','atacado','delivery','promoção'];
    perguntas text[] := ARRAY['Oi, boa tarde! Vocês têm picanha hoje?','Qual o preço do kg da maminha?','Vocês entregam no bairro Centro?','Meu pedido atrasou, pode ver?',
        'Aceitam pix?','Até que horas fica aberto hoje?','Tem pão francês quentinho?','Quero encomendar um bolo pro sábado','Chegou a promoção do hortifruti?','Preciso de nota fiscal da compra'];
    respostas text[] := ARRAY['Olá! Tudo bem? Já vou verificar pra você.','Temos sim! Está R$ 49,90 o kg.','Entregamos sim, a taxa é de R$ 5,00.','Pode deixar, já resolvemos o seu pedido.',
        'Aceitamos pix, cartão e dinheiro.','Abrimos até às 20h todos os dias.','Obrigado pelo contato! Qualquer coisa é só chamar.','Combinado! Anotei aqui o seu pedido.'];
    i int; k int; m int; n int;
    did uuid; conv uuid; aid uuid; op uuid; ago int; passo int;
    aberto timestamptz; iniciado timestamptz; resposta timestamptz; fim timestamptz; ultimo timestamptz; t timestamptz;
    origem text; stat text; env int; rec int; espera int; dur int; ntags int; aceita boolean;
    ult_texto text; ult_status text; ult_nao_lidas int; ult_op uuid; tag text;
    ids_aceitam uuid[]; camp uuid; ncamp int; total int; custo numeric; preco numeric; prio text; sorteio float; mstat text; pos int;
    camp_nomes text[] := ARRAY['Ofertas da semana','Black Friday antecipada','Hortifruti fresquinho','Churrasco de domingo','Pão quente da manhã','Natal antecipado','Volta do atacado'];
    camp_dias int[] := ARRAY[42,35,28,21,14,7,2];
    camp_tags text[] := ARRAY[NULL,'promoção','hortifruti','açougue','padaria','vip','atacado'];
    camp_pausa int[] := ARRAY[0,10,0,30,5,10,60];
    ids_dest uuid[] := ARRAY[]::uuid[];
    ids_conv uuid[] := ARRAY[]::uuid[];
    tags_de uuid[]; tags_nome text[];
    r_tag text; destino uuid;
BEGIN
    IF EXISTS (SELECT 1 FROM destinatario WHERE cliente_id = cid AND telefone LIKE '5500000%') THEN
        RAISE NOTICE 'Dados de demonstração já existem; nada a fazer (rode limpar-dados-demo.sql para refazer).';
        RETURN;
    END IF;
    PERFORM setseed(0.42);

    FOR i IN 1..array_length(nomes, 1) LOOP
        did := gen_random_uuid();
        conv := gen_random_uuid();
        aceita := (i % 5) <> 0;
        INSERT INTO destinatario (id, cliente_id, nome, telefone, aceita_ofertas, tipo)
        VALUES (did, cid, nomes[i], '5500000' || lpad(i::text, 6, '0'), aceita, 'CONTATO');
        ids_dest := ids_dest || did;
        ntags := 1 + floor(random() * 3)::int;
        FOR k IN 1..ntags LOOP
            tag := pool[1 + floor(random() * array_length(pool, 1))::int];
            INSERT INTO destinatario_etiqueta (destinatario_id, etiqueta) VALUES (did, tag) ON CONFLICT DO NOTHING;
        END LOOP;

        op := ops[1 + floor(random() * 3)::int];
        INSERT INTO conversa (id, cliente_id, destinatario_id, status_atendimento, operador_id, nao_lidas)
        VALUES (conv, cid, did, 'FINALIZADO', op, 0);
        ids_conv := ids_conv || conv;

        n := 1 + floor(random() * 4)::int;
        passo := greatest(1, 44 / n);
        ult_texto := NULL;
        FOR k IN REVERSE n..1 LOOP
            ago := (k - 1) * passo + floor(random() * passo)::int;
            IF k = 1 AND i <= 8 THEN ago := 0; END IF;
            origem := CASE WHEN random() < 0.8 THEN 'CLIENTE_FINAL' ELSE 'EMPRESA' END;
            IF ago = 0 AND k = 1 AND i <= 8 THEN
                aberto := now() - make_interval(mins => 5 + floor(random() * 240)::int);
            ELSE
                aberto := (date_trunc('day', now() AT TIME ZONE 'America/Sao_Paulo') - make_interval(days => ago)
                          + make_interval(hours => 8 + floor(random() * 12)::int, mins => floor(random() * 60)::int))
                          AT TIME ZONE 'America/Sao_Paulo';
                IF aberto > now() THEN aberto := now() - interval '3 hours'; END IF;
            END IF;
            espera := 15 + floor(power(random(), 2) * 1500)::int;
            iniciado := aberto + make_interval(secs => espera);
            resposta := CASE WHEN origem = 'CLIENTE_FINAL' THEN iniciado + make_interval(secs => 5 + floor(random() * 180)::int) END;
            dur := 240 + floor(random() * 2700)::int;
            fim := iniciado + make_interval(secs => dur);
            env := 1 + floor(random() * 4)::int;
            rec := CASE WHEN origem = 'CLIENTE_FINAL' THEN 1 + floor(random() * 4)::int ELSE floor(random() * 3)::int END;
            stat := 'FINALIZADO';
            IF k = 1 AND i <= 3 THEN stat := 'AGUARDANDO';
            ELSIF k = 1 AND i <= 6 THEN stat := 'EM_ANDAMENTO';
            ELSIF k = 1 AND i <= 8 THEN stat := 'EM_ESPERA'; END IF;
            IF stat = 'AGUARDANDO' THEN
                origem := 'CLIENTE_FINAL'; iniciado := NULL; resposta := NULL; fim := NULL; env := 0; rec := 1 + floor(random() * 3)::int;
            ELSIF stat <> 'FINALIZADO' THEN
                fim := NULL;
                IF origem = 'CLIENTE_FINAL' AND resposta > now() THEN resposta := iniciado; END IF;
                IF iniciado > now() THEN iniciado := now() - interval '1 minute'; resposta := CASE WHEN origem = 'CLIENTE_FINAL' THEN iniciado END; END IF;
            END IF;
            aid := gen_random_uuid();
            INSERT INTO atendimento (id, conversa_id, cliente_id, origem, status, aberto_em, iniciado_em, primeira_resposta_em,
                                     finalizado_em, mensagens_enviadas, mensagens_recebidas)
            VALUES (aid, conv, cid, origem, stat, aberto, iniciado, resposta, fim, env, rec);

            -- mensagens do atendimento, alternando cliente final e empresa entre a abertura e o fim
            ultimo := coalesce(fim, now());
            pos := 0;
            FOR m IN 1..(env + rec) LOOP
                t := aberto + (ultimo - aberto) * (m - 1)::float / greatest(1, env + rec);
                IF origem = 'CLIENTE_FINAL' AND m = 1 THEN
                    t := aberto;
                END IF;
                IF (origem = 'CLIENTE_FINAL' AND m % 2 = 1 AND pos < rec) OR (origem = 'EMPRESA' AND m % 2 = 0 AND pos < rec) THEN
                    pos := pos + 1;
                    INSERT INTO mensagem (id, conversa_id, remetente_tipo, conteudo, prioridade, status, custo, tentativas, criada_em, atualizada_em)
                    VALUES (gen_random_uuid(), conv, 'DESTINATARIO', perguntas[1 + floor(random() * array_length(perguntas, 1))::int],
                            'NORMAL', 'ENTREGUE', 0, 0, t, t);
                    ult_texto := NULL;
                ELSE
                    IF origem = 'CLIENTE_FINAL' AND m = 2 AND resposta IS NOT NULL THEN t := resposta; END IF;
                    sorteio := random();
                    mstat := CASE WHEN sorteio < 0.55 THEN 'LIDA' WHEN sorteio < 0.9 THEN 'ENTREGUE' WHEN sorteio < 0.96 THEN 'ENVIADA' ELSE 'FALHA' END;
                    INSERT INTO mensagem (id, conversa_id, remetente_tipo, conteudo, prioridade, status, custo, tentativas, criada_em, atualizada_em, operador_id)
                    VALUES (gen_random_uuid(), conv, 'CLIENTE', respostas[1 + floor(random() * array_length(respostas, 1))::int],
                            'NORMAL', mstat, 0.25, 1, t, t, op);
                END IF;
            END LOOP;
            ult_status := stat; ult_nao_lidas := CASE WHEN stat = 'AGUARDANDO' THEN rec ELSE 0 END;
        END LOOP;

        UPDATE conversa c SET status_atendimento = ult_status, nao_lidas = ult_nao_lidas,
               ultima_mensagem = m2.conteudo, data_ultima_mensagem = m2.criada_em
          FROM (SELECT conteudo, criada_em FROM mensagem WHERE conversa_id = conv ORDER BY criada_em DESC LIMIT 1) m2
         WHERE c.id = conv;
    END LOOP;

    -- campanhas em datas diferentes
    FOR ncamp IN 1..array_length(camp_nomes, 1) LOOP
        camp := gen_random_uuid();
        prio := CASE WHEN ncamp IN (2, 6) THEN 'URGENTE' ELSE 'NORMAL' END;
        preco := CASE WHEN prio = 'URGENTE' THEN 0.50 ELSE 0.25 END;
        t := (date_trunc('day', now() AT TIME ZONE 'America/Sao_Paulo') - make_interval(days => camp_dias[ncamp])
              + make_interval(hours => 9 + ncamp)) AT TIME ZONE 'America/Sao_Paulo';
        SELECT array_agg(d.id) INTO ids_aceitam FROM destinatario d
         WHERE d.cliente_id = cid AND d.telefone LIKE '5500000%' AND d.aceita_ofertas
           AND (camp_tags[ncamp] IS NULL OR EXISTS (SELECT 1 FROM destinatario_etiqueta e WHERE e.destinatario_id = d.id AND e.etiqueta = camp_tags[ncamp]));
        total := coalesce(array_length(ids_aceitam, 1), 0);
        IF total = 0 THEN CONTINUE; END IF;
        INSERT INTO campanha (id, cliente_id, nome, conteudo, prioridade, total_destinatarios, custo_total, criada_em)
        VALUES (camp, cid, camp_nomes[ncamp], 'Oferta especial: ' || camp_nomes[ncamp] || '! Aproveite até domingo.', prio, total, total * preco, t);
        IF camp_tags[ncamp] IS NOT NULL OR camp_pausa[ncamp] > 0 THEN
            INSERT INTO campanha_envio (campanha_id, agendada_para, intervalo_segundos, etiqueta)
            VALUES (camp, CASE WHEN ncamp IN (4, 6) THEN t ELSE NULL END, camp_pausa[ncamp], camp_tags[ncamp]);
        END IF;
        pos := 0;
        FOREACH destino IN ARRAY ids_aceitam LOOP
            pos := pos + 1;
            SELECT id INTO conv FROM conversa WHERE destinatario_id = destino;
            sorteio := random();
            mstat := CASE WHEN sorteio < 0.45 THEN 'LIDA' WHEN sorteio < 0.80 THEN 'ENTREGUE' WHEN sorteio < 0.88 THEN 'ENVIADA' ELSE 'FALHA' END;
            INSERT INTO mensagem (id, conversa_id, remetente_tipo, conteudo, prioridade, status, custo, tentativas, criada_em, atualizada_em, campanha_id)
            VALUES (gen_random_uuid(), conv, 'CLIENTE', 'Oferta especial: ' || camp_nomes[ncamp] || '! Aproveite até domingo.', prio, mstat, preco,
                    CASE WHEN mstat = 'FALHA' THEN 3 ELSE 1 END, t + make_interval(secs => pos * camp_pausa[ncamp]), t + make_interval(secs => pos * camp_pausa[ncamp] + 30), camp);
        END LOOP;
    END LOOP;
    RAISE NOTICE 'Dados de demonstração criados: % contatos.', array_length(ids_dest, 1);
END $$;
