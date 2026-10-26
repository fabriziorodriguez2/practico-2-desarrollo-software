import pytest
from flask import render_template
from markupsafe import escape

from app import app


@pytest.mark.parametrize("descripcion", [
    "<script>alert('XSS')</script>",
    '<img src="x" onerror="alert(1)">',
])
def test_descripcion_se_muestra_como_texto(descripcion):
    pelicula = {"id": 1, "nombre": "Dune", "genero": "Ciencia ficción",
                "director": "Denis Villeneuve", "descripcion": descripcion}
    with app.app_context():
        html = render_template("edit.html", pelicula=pelicula)
    assert descripcion not in html
    # La descripción aparece en la vista previa y en el textarea.
    assert html.count(str(escape(descripcion))) == 2


def test_descripcion_normal_sigue_visible():
    pelicula = {"id": 1, "nombre": "Dune", "genero": "Ciencia ficción",
                "director": "Denis Villeneuve", "descripcion": "Una película de ciencia ficción"}
    with app.app_context():
        html = render_template("edit.html", pelicula=pelicula)
    assert html.count(pelicula["descripcion"]) == 2
