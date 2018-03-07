export default (app) => {
    // Shortens so that only scheme, domain, and last 15 characters of an url are displayed
    app.filter('shortenUrl', function () {
        'ngInject';
        return function (input) {
            if (input === '#') {
                return '';
            }
            let segments = input.split('/');
            return `${segments[0]}//${segments[2]}/.../${input.substr(input.length - 15)}`;
        };
    });
};
