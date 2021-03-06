import cz.siret.prank.program.params.Params

(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    dataset_base_dir = "../../p2rank-datasets"

    /**
     * all output of the prorgam will be stored in subdirectores of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    output_base_dir = "../../p2rank-results/2.0-rc.2"

    visualizations = false

    delete_models = true

    delete_vectors = true

    max_train_instances = 0

    /**
     * stop processing a datsaset on the first unrecoverable error with a dataset item
     */
    fail_fast = false

    classifier="FastRandomForest"

    seed = 42

    loop = 10

    predictions = true

    out_prefix_date = false

    crossval_threads = 5

    cache_datasets = true


    log_cases = true

    /**
     * calculate feature importance
     * available only for some classifiers
     */
    feature_importances = false

}
