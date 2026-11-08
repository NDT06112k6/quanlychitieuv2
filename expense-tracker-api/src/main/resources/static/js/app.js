import {startRouter} from './router.js';

const root = document.getElementById('app');
document.querySelector('.skip-link').addEventListener('click', (event) => {
    event.preventDefault();
    document.getElementById('main-content')?.focus();
});
startRouter(root);
