import sqlite3
from unittest.mock import patch

import pytest

from app import app, buscar_funciones
from init_db import init_db


@pytest.fixture
def base_datos(tmp_path, monkeypatch):
    ruta = tmp_path / "cine.db"
    # El inicializador real carga los datos en una base temporal.
    with patch("init_db.sqlite3.connect", return_value=sqlite3.connect(ruta)):
        init_db()
    monkeypatch.setitem(app.config, "DATABASE", str(ruta))
    with app.app_context():
        yield


def test_busqueda_normal_sigue_funcionando(base_datos):
    resultados = buscar_funciones("Dune")
    assert len(resultados) == 3
    assert all(fila["pelicula"] == "Dune: Parte Dos" for fila in resultados)


def test_sqli_no_devuelve_todas_las_funciones(base_datos):
    resultados = buscar_funciones("' OR 1=1 --")
    assert len(resultados) == 0


def test_sentido_no_permite_agregar_sql(base_datos):
    normales = buscar_funciones("Dune")
    resultados = buscar_funciones("Dune", sort_dir="DESC LIMIT 1")
    assert len(resultados) == len(normales) == 3
