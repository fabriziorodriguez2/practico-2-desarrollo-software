from flask import Flask, render_template, request, g
import sqlite3
from config import Config

app = Flask(__name__)
app.config.from_object(Config)


def get_db():
    if 'db' not in g:
        g.db = sqlite3.connect(app.config['DATABASE'])
        g.db.row_factory = sqlite3.Row
    return g.db


@app.teardown_appcontext
def close_db(exception):
    db = g.pop('db', None)
    if db is not None:
        db.close()


def buscar_funciones(query, sort_by='nombre', sort_dir='ASC'):
    db = get_db()

    # Valores permitidos para ordenar.
    columnas_permitidas = {
        'nombre': 'peliculas.nombre',
        'fecha': 'funciones.fecha_hora'
    }

    columna_orden = columnas_permitidas.get(
        sort_by,
        'peliculas.nombre'
    )

    direccion_orden = (
        'DESC'
        if sort_dir.upper() == 'DESC'
        else 'ASC'
    )

    sql = f"""
        SELECT
            peliculas.nombre AS pelicula,
            funciones.fecha_hora,
            (
                funciones.asientos_totales -
                funciones.asientos_ocupados
            ) AS disponibles
        FROM funciones
        JOIN peliculas
            ON funciones.pelicula_id = peliculas.id
        WHERE peliculas.nombre LIKE ?
        ORDER BY {columna_orden} {direccion_orden}
    """

    return db.execute(
        sql,
        (f"%{query}%",)
    ).fetchall()


@app.route('/')
def index():
    query = request.args.get('buscar', '')
    sort_by = request.args.get('ordenar_por', 'nombre')
    sort_dir = request.args.get('sentido', 'ASC')
    resultados = []
    if query:
        resultados = buscar_funciones(query, sort_by, sort_dir)
    return render_template('index.html',
                           resultados=resultados,
                           query=query,
                           sort_by=sort_by,
                           sort_dir=sort_dir)


if __name__ == '__main__':
    import os
    host=os.environ.get('FLASK_HOST', '0.0.0.0')
    debug = os.environ.get('DEBUG_MODE', 'true').lower() in ('true', '1', 'yes')
    app.run(debug=debug,host=host)
