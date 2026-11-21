"""Testes das regras da curadoria: python -m unittest scripts/curadoria/test_gerar_composicao.py"""
import sys
import unittest
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gerar_composicao as g  # noqa: E402


def resultado(rua, numero=None, cidade="São Paulo", uf="BR-SP", cep=None):
    a = {"road": rua, "city": cidade, "ISO3166-2-lvl4": uf}
    if numero:
        a["house_number"] = numero
    if cep:
        a["postcode"] = cep
    return {"lat": "-23.5", "lon": "-46.6", "display_name": rua, "osm_type": "way", "osm_id": 1, "address": a}


class Numeros(unittest.TestCase):
    def test_formato_brasileiro(self):
        self.assertEqual(g.numero("62.587,38"), Decimal("62587.38"))
        self.assertEqual(g.numero("41.930 m²"), Decimal("41930"))
        self.assertEqual(g.numero("130.329,00 m2"), Decimal("130329.00"))

    def test_erro_de_digitacao_da_fonte(self):
        self.assertEqual(g.numero("3.898.63 m²"), Decimal("3898.63"))

    def test_porta(self):
        self.assertEqual(g.numero_da_porta("Rua Humaitá, 275, Humaitá, Rio de Janeiro, RJ"), "275")
        self.assertEqual(g.numero_da_porta("Rua Gomes de Carvalho, 1.195, Vila Olímpia – São Paulo, SP"), "1195")
        self.assertEqual(g.numero_da_porta("Rodovia Fernão Dias, km 863, Pouso Alegre, MG"), None)
        self.assertEqual(g.numero_da_porta("Avenida Piracema, nº 155, quadra 5, lotes 9 a 16, Barueri"), "155")
        self.assertEqual(g.marco_km("Rodovia Fernão Dias, km 863, Pouso Alegre, MG"), "863")
        self.assertIsNone(g.marco_km("Rua dos Pinheiros, 701"))


class Hglg(unittest.TestCase):
    BLOCO = ["1.1.2.1.1", "HGLG Embu", "Estrada Santa Clara, nº 150, Bairro Maria Imaculada", "Área (m2):", "39.475,00",
             "Nº de unidades ou lojas:", "1", "- | 0,0000% | 0,0000% | 1,3190% |", '"-" |', "|", "0,0000% |", "|", "0,0000% |", "|",
             "Extrema", "Estrada Municipal X, Extrema, MG", "Área (m2):", "34.100,63", "Nº de unidades ou lojas:", "1",
             "- | 0,0000% | 0,0000% | 1,4592% |", "Automotivo |", "|", "100,0000% |", "|", "1,4592% |", "|", "1.1.2.1.2"]

    def test_traco_da_fonte_nao_vira_setor(self):
        embu, extrema = g.analisar_hglg(self.BLOCO)
        self.assertEqual(embu["setoresDosInquilinos"], [])
        self.assertEqual(extrema["setoresDosInquilinos"], [{"setor": "Automotivo", "percentualDaReceitaDoImovel": "100"}])
        self.assertEqual(embu["percentualDaReceitaDoFundo"], "1.3190")
        self.assertIsNone(embu["cidade"])


class Cidades(unittest.TestCase):
    def test_extrai_cidade_e_uf(self):
        self.assertEqual(g.cidade_uf("Avenida Parque B, 347, Betim, MG"), ("Betim", "MG"))
        self.assertEqual(g.cidade_uf("Rua do Níquel, 205 – Parque Industrial – Itaquaquecetuba – SP."), ("Itaquaquecetuba", "SP"))
        self.assertEqual(g.cidade_uf("Avenida X, 3000, Bairro Y, na cidade de Belo Horizonte, Estado de Minas Gerais"), ("Belo Horizonte", "MG"))
        self.assertEqual(g.cidade_uf("Rodovia BR 153, Km 5,5"), (None, None))
        self.assertEqual(g.cidade_uf(None), (None, None))


class Validacao(unittest.TestCase):
    PUB = {"uf": "SP", "cidade": "São Paulo", "cep": None, "rua": "Rua dos Pinheiros", "endereco": "Rua dos Pinheiros, 701, São Paulo, SP"}

    def test_aceita_endereco_com_a_mesma_porta(self):
        r = g.aceitar(resultado("Rua dos Pinheiros", "701"), self.PUB)
        self.assertEqual(r["precisao"], "ENDERECO")

    def test_numero_diferente_vira_via(self):
        r = g.aceitar(resultado("Rua dos Pinheiros", "999"), self.PUB)
        self.assertEqual(r["precisao"], "VIA")

    def test_rejeita_cidade_ou_estado_diferentes(self):
        self.assertIsNone(g.aceitar(resultado("Rua dos Pinheiros", "701", cidade="Campinas"), self.PUB))
        self.assertIsNone(g.aceitar(resultado("Rua dos Pinheiros", "701", uf="BR-RJ"), self.PUB))

    def test_rejeita_rua_diferente(self):
        self.assertIsNone(g.aceitar(resultado("Avenida Brasil", "701"), self.PUB))

    def test_rodovia_com_marco_diferente_e_rejeitada(self):
        pub = {"uf": "MG", "cidade": "Pouso Alegre", "cep": None, "rua": "Rodovia Fernão Dias",
               "endereco": "Rodovia Fernão Dias, km 863, Pouso Alegre, MG"}
        r = resultado("Rodovia Fernão Dias", "850", cidade="Pouso Alegre", uf="BR-MG")
        self.assertEqual(g.aceitar(r, pub), "RODOVIA")

    def test_cep_diferente_e_rejeitado(self):
        pub = {"uf": None, "cidade": None, "cep": "15093-340", "rua": "Avenida Presidente Juscelino", "endereco": "Avenida Presidente Juscelino, 5000"}
        self.assertIsNone(g.aceitar(resultado("Avenida Presidente Juscelino", None, cep="01310-000"), pub))
        self.assertIsNotNone(g.aceitar(resultado("Avenida Presidente Juscelino", None, cep="15093-340"), pub))


if __name__ == "__main__":
    unittest.main()
