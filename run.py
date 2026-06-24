from app import create_app

app = create_app()

if __name__ == '__main__':
    # use_reloader=False prevents APScheduler from starting twice in debug mode
    app.run(debug=True, use_reloader=False, host='0.0.0.0', port=5000)
