# Docker Jepsen

To build a Docker Jepsen image for running atomix-jepsen:

1.  From this directory, run:

	```
	docker build -t jepsen .
	```

2.  Start the container and run `build-docker-jepsen.sh` which will build a sub-container image:

    ```
    docker run --privileged -it --name initial-jepsen jepsen

    > build-docker-jepsen.sh
    ```

3.  From another window, commit the final image and delete the container:

    ```
    docker commit initial-jepsen jepsen
    docker rm -f initial-jepsen
    ```
    
## Gratitude

Thanks to [@tjake](https://github.com/tjake) for the original Dockerized Jepsen work which this was completely based on.