import {Map} from 'immutable';
export default (app) => {
    class RasterFoundryRepository {
        constructor(
            $q, $filter,
            authService, datasourceService, sceneService, thumbnailService,
            projectService
        ) {
            this.$q = $q;
            this.$filter = $filter;
            this.authService = authService;
            this.datasourceService = datasourceService;
            this.sceneService = sceneService;
            this.thumbnailService = thumbnailService;
            this.projectService = projectService;
            this.datasourceCache = new Map();
            this.previewOnMap = true;
        }

        initRepository() {
            return this.$q((resolve) => {
                resolve();
            });
        }

        getFilters() {
            return [{
                param: 'datasource',
                label: 'Imagery Sources',
                type: 'search-select',
                getSources: this.getSources.bind(this)
            }, {
                params: {
                    min: 'minAcquisitionDatetime',
                    max: 'maxAcquisitionDatetime'
                },
                label: 'Date Range',
                type: 'daterange',
                default: 'None'
            // TODO enable this filter once the api supports it
            // }, {
            //     type: 'shape',
            //     label: 'Area of Interest',
            //     param: 'shape'
            }, {
                params: {
                    min: 'minCloudCover',
                    max: 'maxCloudCover'
                },
                label: 'Cloud Cover',
                type: 'slider',
                min: 0,
                max: 100,
                ticks: 10,
                step: 10,
                scale: 1
            }, {
                params: {
                    min: 'minSunElevation',
                    max: 'maxSunElevation'
                },
                label: 'Sun Elevation',
                type: 'slider',
                min: 0,
                max: 180,
                ticks: 30,
                step: 10,
                scale: 1
            }, {
                params: {
                    min: 'minSunAzimuth',
                    max: 'maxSunAzimuth'
                },
                label: 'Sun Azimuth',
                type: 'slider',
                min: 0,
                max: 360,
                ticks: 60,
                step: 10,
                scale: 1
            }, {
                param: 'ingested',
                label: 'Ingest Status',
                type: 'tag',
                options: [{
                    label: 'Show all',
                    value: null
                }, {
                    label: 'Uningested Only',
                    value: 'false'
                }, {
                    label: 'Ingested Only',
                    value: 'true'
                }]
            }, {
                param: 'owner',
                label: 'Owner',
                type: 'tag',
                options: [{
                    label: 'All',
                    value: null
                }, {
                    label: 'My Scenes',
                    value: this.authService.getProfile().sub
                }]
            }];
        }

        getSources() {
            return this.$q((resolve, reject) => {
                this.datasourceService.query({
                    sort: 'name,asc'
                }).then(response => {
                    resolve(response.results);
                }, (err) => {
                    reject(err);
                });
            });
        }

        /*
          Returns a function which fetches scenes

          Function chain:
          (filters) => (bbox) => () => Future(next page of scenes)
        */
        fetchScenes(filters) {
            const params = Object.assign({}, filters);
            if (filters.shape) {
                filters.shape = filters.shape.id;
            }

            const fetchForBbox = (bbox) => {
                let hasNext = null;
                let page = 0;
                let requestTime = new Date().toISOString();

                return () => {
                    return this.$q((resolve, reject) => {
                        if (hasNext !== null && !hasNext) {
                            reject('No more scenes to fetch.');
                        }
                        this.sceneService.query(
                            Object.assign({
                                sort: 'acquisitionDatetime,desc',
                                pageSize: '20',
                                page,
                                bbox,
                                maxCreateDatetime: requestTime
                            }, params)
                        ).then((response) => {
                            // We aren't supporting concurrent scene paged requests
                            page = page + 1;
                            hasNext = response.hasNext;
                            resolve({
                                scenes: response.results,
                                hasNext,
                                count: this.$filter('number')(response.count)
                            });
                        }, (error) => {
                            reject({
                                error
                            });
                        });
                    });
                };
            };

            return fetchForBbox;
        }

        getThumbnail(scene) {
            return this.$q((resolve, reject) => {
                if (scene.thumbnails.length) {
                    resolve(this.thumbnailService.getBestFitUrl(scene.thumbnails, 75));
                } else {
                    reject();
                }
            });
        }

        getPreview(scene) {
            return this.$q((resolve, reject) => {
                if (scene.thumbnails.length) {
                    resolve(this.thumbnailService.getBestFitUrl(scene.thumbnails, 1000));
                } else {
                    reject();
                }
            });
        }

        getDatasource(scene) {
            return this.$q((resolve, reject) => {
                if (this.datasourceCache.has(scene.datasource)) {
                    let datasource = this.datasourceCache.get(scene.datasource);
                    if (datasource.then) {
                        datasource.then((d) => {
                            resolve(d);
                        });
                    } else {
                        resolve(datasource);
                    }
                } else {
                    this.datasourceCache = this.datasourceCache.set(
                        scene.datasource,
                        this.datasourceService
                            .get(scene.datasource)
                            .then((datasource) => {
                                resolve(datasource);
                                return datasource;
                            }, (err) => {
                                reject(err);
                            })
                    );
                }
            });
        }


        /*
          Returns a function which adds the given RF scenes to the project
         */
        addToProject(projectId, scenes) {
            return this.projectService.addScenes(projectId, scenes.map(scene => scene.id));
        }

        getScenePermissions(scene) {
            let result = [];
            if (scene) {
                result.push('download');
            }
            return result;
        }
    }

    app.service('RasterFoundryRepository', RasterFoundryRepository);
};
