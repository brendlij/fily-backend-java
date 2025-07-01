# Fily – Self-hosted Filebrowser Backend

A modern, minimalist file browser backend built with Java Spring Boot.  
**Features:** Upload, download (folders as ZIP!), rename, delete (recursive), create folders, all controlled via REST API.

---

## Features

- List, create, rename, delete files & folders (recursive)
- File upload (to any path)
- Download files & folders (folders are automatically zipped)
- **Secure path validation** (prevents path traversal)
- Docker & cloud ready (customizable storage location)

---

## API Endpoints (Examples)

| Action                  | Method   | Endpoint                        | Parameters         |
|-------------------------|----------|----------------------------------|--------------------|
| List directory contents | GET      | `/api/files?path=FOLDER`         | path (optional)    |
| Upload file             | POST     | `/api/files/upload`              | file, path         |
| Download file/folder    | GET      | `/api/files/download`            | path               |
| Create folder           | POST     | `/api/files/mkdir`               | path               |
| Delete file/folder      | DELETE   | `/api/files?path=TARGET`         | path               |
| Rename file/folder      | POST     | `/api/files/rename`              | oldPath, newName   |

---

## Configuration

Set your **storage directory** in `application.properties`:

```properties
fileserver.basedir=C:/Users/juli/Documents/TEST
```
*Change the path to your preferred location.*

---

## Build & Run

```bash
mvn clean install
java -jar target/*.jar
```
Default port is **8080**.

---

## Example: Upload a file (curl)

```bash
curl -F "file=@example.jpg" "http://localhost:8080/api/files/upload?path=subfolder"
```

---

## Security

- Prevents path traversal (no `..` allowed in paths)
- Error handling: returns status & messages on errors

---

## License

MIT  
© 2024 YOUR NAME

---

> Frontend recommended: React/Next.js/Mantine – everything works via REST API!
