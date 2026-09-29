import './Favorites.css';

// Same editorial 3D cover used by the search and favorites screens. The seed ISBNs
// are not real, so Open Library has no cover images to show.
export default function BookCover({ book }) {
    return (
        <div className="book-3d-cover" role="img" aria-label={`Portada de ${book.titulo}`}>
            <div className="book-3d-spine"></div>
            <div className="book-3d-body">
                <div className="book-3d-border">
                    <span className="book-3d-tag">{book.nombreLibreria || 'EDICIÓN ESPECIAL'}</span>
                    <h4 className="book-3d-title">{book.titulo}</h4>
                    <p className="book-3d-author">{book.autor}</p>
                </div>
            </div>
        </div>
    );
}
